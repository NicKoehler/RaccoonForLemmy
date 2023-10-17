package com.github.diegoberaldin.raccoonforlemmy.core.commonui.postdetail

import cafe.adriel.voyager.core.model.ScreenModel
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.repository.ThemeRepository
import com.github.diegoberaldin.raccoonforlemmy.core.architecture.DefaultMviModel
import com.github.diegoberaldin.raccoonforlemmy.core.architecture.MviModel
import com.github.diegoberaldin.raccoonforlemmy.core.notifications.NotificationCenter
import com.github.diegoberaldin.raccoonforlemmy.core.notifications.NotificationCenterContractKeys
import com.github.diegoberaldin.raccoonforlemmy.core.persistence.repository.SettingsRepository
import com.github.diegoberaldin.raccoonforlemmy.core.utils.HapticFeedback
import com.github.diegoberaldin.raccoonforlemmy.core.utils.ShareHelper
import com.github.diegoberaldin.raccoonforlemmy.domain.identity.repository.IdentityRepository
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.CommentModel
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.PostModel
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.SortType
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.shareUrl
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.toSortType
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.repository.CommentRepository
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.repository.PostRepository
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.repository.SiteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class PostDetailViewModel(
    private val mvi: DefaultMviModel<PostDetailMviModel.Intent, PostDetailMviModel.UiState, PostDetailMviModel.Effect>,
    private val post: PostModel,
    private val otherInstance: String,
    private val highlightCommentId: Int?,
    private val identityRepository: IdentityRepository,
    private val siteRepository: SiteRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val themeRepository: ThemeRepository,
    private val settingsRepository: SettingsRepository,
    private val shareHelper: ShareHelper,
    private val notificationCenter: NotificationCenter,
    private val hapticFeedback: HapticFeedback,
) : MviModel<PostDetailMviModel.Intent, PostDetailMviModel.UiState, PostDetailMviModel.Effect> by mvi,
    ScreenModel {

    private var currentPage: Int = 1
    private var highlightCommentPath: String? = null
    private var commentWasHighlighted = false
    private var expandedTopLevelComments = mutableListOf<Int>()

    init {
        notificationCenter.addObserver({
            (it as? PostModel)?.also { post ->
                handlePostUpdate(post)
            }
        }, this::class.simpleName.orEmpty(), NotificationCenterContractKeys.PostUpdated)
    }

    fun finalize() {
        notificationCenter.removeObserver(this::class.simpleName.orEmpty())
    }

    override fun onStarted() {
        mvi.onStarted()

        mvi.scope?.launch(Dispatchers.Main) {
            themeRepository.postLayout.onEach { layout ->
                mvi.updateState { it.copy(postLayout = layout) }
            }.launchIn(this)

            settingsRepository.currentSettings.onEach { settings ->
                mvi.updateState {
                    it.copy(
                        swipeActionsEnabled = settings.enableSwipeActions,
                        sortType = settings.defaultCommentSortType.toSortType(),
                        separateUpAndDownVotes = settings.separateUpAndDownVotes,
                        autoLoadImages = settings.autoLoadImages,
                    )
                }
            }.launchIn(this)

        }

        mvi.scope?.launch(Dispatchers.IO) {
            if (uiState.value.currentUserId == null) {
                val auth = identityRepository.authToken.value.orEmpty()
                val user = siteRepository.getCurrentUser(auth)
                mvi.updateState {
                    it.copy(
                        currentUserId = user?.id ?: 0,
                    )
                }
            }

            mvi.updateState {
                it.copy(post = post)
            }

            val auth = identityRepository.authToken.value
            val updatedPost = postRepository.get(
                id = post.id,
                auth = auth,
                instance = otherInstance,
            )
            if (updatedPost != null) {
                mvi.updateState {
                    it.copy(post = updatedPost)
                }
            }

            if (highlightCommentId != null) {
                val auth = identityRepository.authToken.value
                val comment = commentRepository.getBy(
                    id = highlightCommentId,
                    auth = auth,
                    instance = otherInstance,
                )
                highlightCommentPath = comment?.path
            }
            if (mvi.uiState.value.comments.isEmpty()) {
                refresh()
            }
        }
    }

    private fun downloadUntilHighlight() {
        val highlightPath = highlightCommentPath ?: return

        val indexOfHighlight = uiState.value.comments.indexOfFirst {
            it.path == highlightPath
        }
        if (indexOfHighlight == -1) {
            val lastCommentOfThread = uiState.value.comments.filter {
                highlightPath.startsWith(it.path)
            }.takeIf { it.isNotEmpty() }?.maxBy { it.depth }
            if (lastCommentOfThread != null) {
                // comment has an ancestor in the list, go down that path
                loadMoreComments(
                    parentId = lastCommentOfThread.id,
                    loadUntilHighlight = true,
                )
            } else {
                // no ancestor of the comment on this pages, check the next one
                loadNextPage()
            }
        } else {
            // comment to highlight found
            commentWasHighlighted = true
            mvi.scope?.launch {
                mvi.emitEffect(PostDetailMviModel.Effect.ScrollToComment(indexOfHighlight))
            }
        }
    }

    override fun reduce(intent: PostDetailMviModel.Intent) {
        when (intent) {
            PostDetailMviModel.Intent.LoadNextPage -> {
                if (!uiState.value.initial) {
                    loadNextPage()
                }
            }

            PostDetailMviModel.Intent.Refresh -> refresh()
            PostDetailMviModel.Intent.RefreshPost -> refreshPost()
            PostDetailMviModel.Intent.HapticIndication -> hapticFeedback.vibrate()
            is PostDetailMviModel.Intent.ChangeSort -> applySortType(intent.value)

            is PostDetailMviModel.Intent.DownVoteComment -> toggleDownVoteComment(
                comment = uiState.value.comments.first { it.id == intent.commentId },
                feedback = intent.feedback,
            )

            is PostDetailMviModel.Intent.DownVotePost -> toggleDownVotePost(
                post = uiState.value.post,
                feedback = intent.feedback,
            )

            is PostDetailMviModel.Intent.SaveComment -> toggleSaveComment(
                comment = uiState.value.comments.first { it.id == intent.commentId },
                feedback = intent.feedback,
            )

            is PostDetailMviModel.Intent.SavePost -> toggleSavePost(
                post = intent.post,
                feedback = intent.feedback,
            )

            is PostDetailMviModel.Intent.UpVoteComment -> toggleUpVoteComment(
                comment = uiState.value.comments.first { it.id == intent.commentId },
                feedback = intent.feedback,
            )

            is PostDetailMviModel.Intent.UpVotePost -> toggleUpVotePost(
                post = uiState.value.post,
                feedback = intent.feedback,
            )

            is PostDetailMviModel.Intent.FetchMoreComments -> {
                loadMoreComments(intent.parentId)
            }

            is PostDetailMviModel.Intent.DeleteComment -> deleteComment(intent.commentId)
            PostDetailMviModel.Intent.DeletePost -> deletePost()
            PostDetailMviModel.Intent.SharePost -> share(
                post = uiState.value.post,
            )

            is PostDetailMviModel.Intent.ToggleExpandComment -> {
                val comment = uiState.value.comments.first { it.id == intent.commentId }
                toggleExpanded(comment)
            }
        }
    }

    private fun refreshPost() {
        mvi.scope?.launch(Dispatchers.IO) {
            val auth = identityRepository.authToken.value
            val updatedPost = postRepository.get(
                id = post.id,
                auth = auth,
                instance = otherInstance,
            ) ?: post
            mvi.updateState {
                it.copy(post = updatedPost)
            }
        }
    }

    private fun refresh() {
        currentPage = 1
        mvi.updateState { it.copy(canFetchMore = true, refreshing = true) }
        loadNextPage()
    }

    private fun loadNextPage() {
        val currentState = mvi.uiState.value
        if (!currentState.canFetchMore || currentState.loading) {
            mvi.updateState { it.copy(refreshing = false) }
            return
        }
        val autoExpandComments = settingsRepository.currentSettings.value.autoExpandComments

        mvi.scope?.launch(Dispatchers.IO) {
            mvi.updateState { it.copy(loading = true) }
            val auth = identityRepository.authToken.value
            val refreshing = currentState.refreshing
            val sort = currentState.sortType
            val itemList = commentRepository.getAll(
                auth = auth,
                postId = post.id,
                instance = otherInstance,
                page = currentPage,
                sort = sort,
                maxDepth = CommentRepository.MAX_COMMENT_DEPTH,
            )?.processCommentsToGetNestedOrder(
                ancestorId = null,
            ).let {
                if (refreshing) {
                    it
                } else {
                    it?.filter { c1 ->
                        // prevents accidental duplication
                        currentState.comments.none { c2 -> c1.id == c2.id }
                    }
                }
            }?.let {
                if (autoExpandComments) {
                    expandedTopLevelComments =
                        it.filter { c -> c.depth == 0 }.map { c -> c.id }.toMutableList()
                }
                it
            }?.applyExpansionFilter(
                expandedTopLevelCommentIds = expandedTopLevelComments,
            )

            if (!itemList.isNullOrEmpty()) {
                currentPage++
            }
            mvi.updateState {
                val newcomments = if (refreshing) {
                    itemList.orEmpty()
                } else {
                    it.comments + itemList.orEmpty()
                }
                it.copy(
                    comments = newcomments,
                    loading = false,
                    canFetchMore = itemList?.isEmpty() != true,
                    refreshing = false,
                    initial = false,
                )

            }
            if (highlightCommentPath != null && !commentWasHighlighted) {
                downloadUntilHighlight()
            }
        }
    }

    private fun applySortType(value: SortType) {
        mvi.updateState { it.copy(sortType = value) }
        refresh()
    }

    private fun handlePostUpdate(post: PostModel) {
        mvi.updateState {
            it.copy(post = post)
        }
    }

    private fun loadMoreComments(parentId: Int, loadUntilHighlight: Boolean = false) {
        val autoExpandComments = settingsRepository.currentSettings.value.autoExpandComments
        mvi.scope?.launch(Dispatchers.IO) {
            val currentState = mvi.uiState.value
            val auth = identityRepository.authToken.value
            val sort = currentState.sortType
            val fetchResult = commentRepository.getChildren(
                auth = auth,
                parentId = parentId,
                instance = otherInstance,
                sort = sort,
                maxDepth = CommentRepository.MAX_COMMENT_DEPTH,
            )?.processCommentsToGetNestedOrder(
                ancestorId = parentId.toString(),
            )?.let {
                if (autoExpandComments) {
                    expandedTopLevelComments =
                        it.filter { c -> c.depth == 0 }.map { c -> c.id }.toMutableList()
                }
                it
            }?.applyExpansionFilter(
                expandedTopLevelCommentIds = expandedTopLevelComments,
            )
            val newList = uiState.value.comments.let { list ->
                val index = list.indexOfFirst { c -> c.id == parentId }
                list.toMutableList().apply {
                    addAll(index + 1, fetchResult.orEmpty())
                }.toList()
            }

            mvi.updateState { it.copy(comments = newList) }
            if (loadUntilHighlight) {
                // start indirect recursion
                downloadUntilHighlight()
            }
        }
    }

    private fun toggleUpVotePost(
        post: PostModel,
        feedback: Boolean,
    ) {
        val newValue = post.myVote <= 0
        if (feedback) {
            hapticFeedback.vibrate()
        }
        val newPost = postRepository.asUpVoted(
            post = post,
            voted = newValue,
        )
        mvi.updateState { it.copy(post = newPost) }
        mvi.scope?.launch(Dispatchers.IO) {
            try {
                val auth = identityRepository.authToken.value.orEmpty()
                postRepository.upVote(
                    auth = auth,
                    post = post,
                    voted = newValue,
                )
                notificationCenter.getAllObservers(NotificationCenterContractKeys.PostUpdated)
                    .forEach {
                        it.invoke(newPost)
                    }
            } catch (e: Throwable) {
                e.printStackTrace()
                mvi.updateState { it.copy(post = post) }
            }
        }
    }

    private fun toggleDownVotePost(
        post: PostModel,
        feedback: Boolean,
    ) {
        val newValue = post.myVote >= 0
        if (feedback) {
            hapticFeedback.vibrate()
        }
        val newPost = postRepository.asDownVoted(
            post = post,
            downVoted = newValue,
        )
        mvi.updateState { it.copy(post = newPost) }
        mvi.scope?.launch(Dispatchers.IO) {
            try {
                val auth = identityRepository.authToken.value.orEmpty()
                postRepository.downVote(
                    auth = auth,
                    post = post,
                    downVoted = newValue,
                )
                notificationCenter.getAllObservers(NotificationCenterContractKeys.PostUpdated)
                    .forEach {
                        it.invoke(newPost)
                    }
            } catch (e: Throwable) {
                e.printStackTrace()
                mvi.updateState { it.copy(post = post) }
            }
        }
    }

    private fun toggleSavePost(
        post: PostModel,
        feedback: Boolean,
    ) {
        val newValue = !post.saved
        if (feedback) {
            hapticFeedback.vibrate()
        }
        val newPost = postRepository.asSaved(
            post = post,
            saved = newValue,
        )
        mvi.updateState { it.copy(post = newPost) }
        mvi.scope?.launch(Dispatchers.IO) {
            try {
                val auth = identityRepository.authToken.value.orEmpty()
                postRepository.save(
                    auth = auth,
                    post = post,
                    saved = newValue,
                )
                notificationCenter.getAllObservers(NotificationCenterContractKeys.PostUpdated)
                    .forEach {
                        it.invoke(newPost)
                    }
            } catch (e: Throwable) {
                e.printStackTrace()
                mvi.updateState { it.copy(post = post) }
            }
        }
    }

    private fun toggleUpVoteComment(
        comment: CommentModel,
        feedback: Boolean,
    ) {
        val newValue = comment.myVote <= 0
        if (feedback) {
            hapticFeedback.vibrate()
        }
        val newComment = commentRepository.asUpVoted(
            comment = comment,
            voted = newValue,
        )
        mvi.updateState {
            it.copy(
                comments = it.comments.map { c ->
                    if (c.id == comment.id) {
                        newComment
                    } else {
                        c
                    }
                },
            )
        }
        mvi.scope?.launch(Dispatchers.IO) {
            try {
                val auth = identityRepository.authToken.value.orEmpty()
                commentRepository.upVote(
                    auth = auth,
                    comment = comment,
                    voted = newValue,
                )
            } catch (e: Throwable) {
                e.printStackTrace()
                mvi.updateState {
                    it.copy(
                        comments = it.comments.map { c ->
                            if (c.id == comment.id) {
                                comment
                            } else {
                                c
                            }
                        },
                    )
                }
            }
        }
    }

    private fun toggleDownVoteComment(
        comment: CommentModel,
        feedback: Boolean,
    ) {
        val newValue = comment.myVote >= 0
        if (feedback) {
            hapticFeedback.vibrate()
        }
        val newComment = commentRepository.asDownVoted(comment, newValue)
        mvi.updateState {
            it.copy(
                comments = it.comments.map { c ->
                    if (c.id == comment.id) {
                        newComment
                    } else {
                        c
                    }
                },
            )
        }
        mvi.scope?.launch(Dispatchers.IO) {
            try {
                val auth = identityRepository.authToken.value.orEmpty()
                commentRepository.downVote(
                    auth = auth,
                    comment = comment,
                    downVoted = newValue,
                )
            } catch (e: Throwable) {
                e.printStackTrace()
                mvi.updateState {
                    it.copy(
                        comments = it.comments.map { c ->
                            if (c.id == comment.id) {
                                comment
                            } else {
                                c
                            }
                        },
                    )
                }
            }
        }
    }

    private fun toggleSaveComment(
        comment: CommentModel,
        feedback: Boolean,
    ) {
        val newValue = !comment.saved
        if (feedback) {
            hapticFeedback.vibrate()
        }
        val newComment = commentRepository.asSaved(
            comment = comment,
            saved = newValue,
        )
        mvi.updateState {
            it.copy(
                comments = it.comments.map { c ->
                    if (c.id == comment.id) {
                        newComment
                    } else {
                        c
                    }
                },
            )
        }
        mvi.scope?.launch(Dispatchers.IO) {
            try {
                val auth = identityRepository.authToken.value.orEmpty()
                commentRepository.save(
                    auth = auth,
                    comment = comment,
                    saved = newValue,
                )
            } catch (e: Throwable) {
                e.printStackTrace()
                mvi.updateState {
                    it.copy(
                        comments = it.comments.map { c ->
                            if (c.id == comment.id) {
                                comment
                            } else {
                                c
                            }
                        },
                    )
                }
            }
        }
    }

    private fun deleteComment(id: Int) {
        mvi.scope?.launch(Dispatchers.IO) {
            val auth = identityRepository.authToken.value.orEmpty()
            commentRepository.delete(id, auth)
            refresh()
            refreshPost()
        }
    }

    private fun deletePost() {
        mvi.scope?.launch(Dispatchers.IO) {
            val auth = identityRepository.authToken.value.orEmpty()
            postRepository.delete(id = post.id, auth = auth)
            notificationCenter.getObserver(NotificationCenterContractKeys.PostDeleted)?.also {
                it.invoke(post)
            }
            mvi.emitEffect(PostDetailMviModel.Effect.Close)
        }
    }

    private fun share(post: PostModel) {
        val url = post.shareUrl
        if (url.isNotEmpty()) {
            shareHelper.share(url, "text/plain")
        }
    }

    private fun toggleExpanded(comment: CommentModel) {
        if (comment.depth > 0) {
            return
        }
        val id = comment.id
        if (expandedTopLevelComments.contains(id)) {
            expandedTopLevelComments -= id
        } else {
            expandedTopLevelComments += id
        }
        mvi.updateState {
            val newComments = it.comments.applyExpansionFilter(
                expandedTopLevelCommentIds = expandedTopLevelComments,
            )
            it.copy(comments = newComments)
        }
    }
}


private data class Node(
    val comment: CommentModel?,
    val children: MutableList<Node> = mutableListOf(),
)

private fun findNode(id: String, node: Node): Node? {
    if (node.comment?.id.toString() == id) {
        return node
    }
    for (c in node.children) {
        val res = findNode(id, c)
        if (res != null) {
            return res
        }
    }
    return null
}


private fun linearize(node: Node, list: MutableList<CommentModel>) {
    if (node.children.isEmpty()) {
        if (node.comment != null) {
            list.add(node.comment)
        }
        return
    }
    for (c in node.children) {
        linearize(c, list)
    }
    if (node.comment != null) {
        list.add(node.comment)
    }
}

private fun List<CommentModel>.processCommentsToGetNestedOrder(
    ancestorId: String? = null,
): List<CommentModel> {
    val root = Node(null)
    // reconstructs the tree
    for (c in this) {
        val parentId = c.parentId
        if (parentId == ancestorId) {
            root.children += Node(c)
        } else if (parentId != null) {
            val parent = findNode(parentId, root)
            if (parent != null) {
                parent.children += Node(c)
            }
        }
    }

    // linearize the tree depth first
    val result = mutableListOf<CommentModel>()
    linearize(root, result)

    return result.reversed().toList()
}

private fun List<CommentModel>.applyExpansionFilter(
    expandedTopLevelCommentIds: List<Int>,
): List<CommentModel> = map { comment ->
    val visible = comment.depth == 0 || expandedTopLevelCommentIds.any { e ->
        e == comment.path.split(".")[1].toInt()
    }
    val indicatorExpanded = when {
        comment.depth > 0 -> null
        (comment.comments ?: 0) == 0 -> null
        else -> expandedTopLevelCommentIds.contains(comment.id)
    }
    comment.copy(visible = visible, expanded = indicatorExpanded)
}