package com.github.diegoberaldin.raccoonforlemmy.core.commonui.postdetail

import cafe.adriel.voyager.core.model.ScreenModel
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.repository.ThemeRepository
import com.github.diegoberaldin.raccoonforlemmy.core.architecture.DefaultMviModel
import com.github.diegoberaldin.raccoonforlemmy.core.architecture.MviModel
import com.github.diegoberaldin.raccoonforlemmy.core.notifications.NotificationCenter
import com.github.diegoberaldin.raccoonforlemmy.core.notifications.NotificationCenterContractKeys
import com.github.diegoberaldin.raccoonforlemmy.core.preferences.KeyStoreKeys
import com.github.diegoberaldin.raccoonforlemmy.core.preferences.TemporaryKeyStore
import com.github.diegoberaldin.raccoonforlemmy.core.utils.HapticFeedback
import com.github.diegoberaldin.raccoonforlemmy.core.utils.ShareHelper
import com.github.diegoberaldin.raccoonforlemmy.domain.identity.repository.IdentityRepository
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.CommentModel
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.PostModel
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.SortType
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.shareUrl
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.toSortType
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.repository.CommentRepository
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.repository.PostsRepository
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.repository.SiteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class PostDetailViewModel(
    private val mvi: DefaultMviModel<PostDetailMviModel.Intent, PostDetailMviModel.UiState, PostDetailMviModel.Effect>,
    private val post: PostModel,
    private val identityRepository: IdentityRepository,
    private val siteRepository: SiteRepository,
    private val postsRepository: PostsRepository,
    private val commentRepository: CommentRepository,
    private val themeRepository: ThemeRepository,
    private val shareHelper: ShareHelper,
    private val keyStore: TemporaryKeyStore,
    private val notificationCenter: NotificationCenter,
    private val hapticFeedback: HapticFeedback,
) : MviModel<PostDetailMviModel.Intent, PostDetailMviModel.UiState, PostDetailMviModel.Effect> by mvi,
    ScreenModel {

    private var currentPage: Int = 1

    override fun onStarted() {
        mvi.onStarted()
        val sortType = keyStore[KeyStoreKeys.DefaultCommentSortType, 3].toSortType()
        val swipeActionsEnabled = keyStore[KeyStoreKeys.EnableSwipeActions, true]
        mvi.updateState {
            it.copy(
                post = post,
                sortType = sortType,
                swipeActionsEnabled = swipeActionsEnabled,
            )
        }
        mvi.scope?.launch {
            themeRepository.postLayout.onEach { layout ->
                mvi.updateState { it.copy(postLayout = layout) }
            }.launchIn(this)

            if (uiState.value.currentUserId == null) {
                val auth = identityRepository.authToken.value.orEmpty()
                val user = siteRepository.getCurrentUser(auth)
                mvi.updateState {
                    it.copy(
                        currentUserId = user?.id ?: 0,
                    )
                }
            }
            if (post.title.isEmpty()) {
                // empty post must be loaded
                postsRepository.get(post.id)?.also { updatedPost ->
                    mvi.updateState {
                        it.copy(
                            post = updatedPost,
                        )
                    }
                }
            }
            if (mvi.uiState.value.comments.isEmpty()) {
                refresh()
            }
        }
    }

    override fun reduce(intent: PostDetailMviModel.Intent) {
        when (intent) {
            PostDetailMviModel.Intent.LoadNextPage -> loadNextPage()
            PostDetailMviModel.Intent.Refresh -> refresh()
            PostDetailMviModel.Intent.RefreshPost -> refreshPost()
            PostDetailMviModel.Intent.HapticIndication -> hapticFeedback.vibrate()
            is PostDetailMviModel.Intent.ChangeSort -> applySortType(intent.value)

            is PostDetailMviModel.Intent.DownVoteComment -> toggleDownVoteComment(
                comment = uiState.value.comments[intent.index],
                feedback = intent.feedback,
            )

            is PostDetailMviModel.Intent.DownVotePost -> toggleDownVotePost(
                post = uiState.value.post,
                feedback = intent.feedback,
            )

            is PostDetailMviModel.Intent.SaveComment -> toggleSaveComment(
                comment = uiState.value.comments[intent.index],
                feedback = intent.feedback,
            )

            is PostDetailMviModel.Intent.SavePost -> toggleSavePost(
                post = intent.post,
                feedback = intent.feedback,
            )

            is PostDetailMviModel.Intent.UpVoteComment -> toggleUpVoteComment(
                comment = uiState.value.comments[intent.index],
                feedback = intent.feedback,
            )

            is PostDetailMviModel.Intent.UpVotePost -> toggleUpVotePost(
                post = uiState.value.post,
                feedback = intent.feedback,
            )

            is PostDetailMviModel.Intent.FetchMoreComments -> {
                loadMoreComments(intent.parentId)
            }

            is PostDetailMviModel.Intent.DeleteComment -> deleteComment(intent.id)
            PostDetailMviModel.Intent.DeletePost -> deletePost()
            PostDetailMviModel.Intent.SharePost -> share(
                post = uiState.value.post,
            )
        }
    }

    private fun refreshPost() {
        mvi.scope?.launch(Dispatchers.IO) {
            val auth = identityRepository.authToken.value
            val updatedPost = postsRepository.get(id = post.id, auth = auth) ?: post
            mvi.updateState {
                it.copy(
                    post = updatedPost,
                )
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

        mvi.scope?.launch(Dispatchers.IO) {
            mvi.updateState { it.copy(loading = true) }
            val auth = identityRepository.authToken.value
            val refreshing = currentState.refreshing
            val sort = currentState.sortType
            val commentList = commentRepository.getAll(
                auth = auth,
                postId = post.id,
                page = currentPage,
                sort = sort,
                maxDepth = CommentRepository.MAX_COMMENT_DEPTH,
            ).let {
                processCommentsToGetNestedOrder(
                    items = it,
                )
            }
            currentPage++
            val topLevelItems = commentList.filter { it.depth == 0 }
            val canFetchMore = topLevelItems.size >= CommentRepository.DEFAULT_PAGE_SIZE
            mvi.updateState {
                val newcomments = if (refreshing) {
                    commentList
                } else {
                    it.comments + commentList
                }
                it.copy(
                    comments = newcomments,
                    loading = false,
                    canFetchMore = canFetchMore,
                    refreshing = false,
                )
            }
        }
    }

    private fun applySortType(value: SortType) {
        mvi.updateState { it.copy(sortType = value) }
        refresh()
    }

    private fun loadMoreComments(parentId: Int) {
        mvi.scope?.launch(Dispatchers.IO) {
            val currentState = mvi.uiState.value
            val auth = identityRepository.authToken.value
            val sort = currentState.sortType
            val fetchResult = commentRepository.getChildren(
                auth = auth,
                parentId = parentId,
                sort = sort,
                maxDepth = CommentRepository.MAX_COMMENT_DEPTH,
            ).let {
                processCommentsToGetNestedOrder(
                    items = it,
                    ancestorId = parentId.toString(),
                )
            }
            val newList = uiState.value.comments.let { list ->
                val index = list.indexOfFirst { c -> c.id == parentId }
                list.toMutableList().apply {
                    addAll(index + 1, fetchResult)
                }.toList()
            }
            mvi.updateState { it.copy(comments = newList) }
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
        val newPost = postsRepository.asUpVoted(
            post = post,
            voted = newValue,
        )
        mvi.updateState { it.copy(post = newPost) }
        mvi.scope?.launch(Dispatchers.IO) {
            try {
                val auth = identityRepository.authToken.value.orEmpty()
                postsRepository.upVote(
                    auth = auth,
                    post = post,
                    voted = newValue,
                )
                notificationCenter.getObserver(NotificationCenterContractKeys.PostUpdated)?.also {
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
        val newPost = postsRepository.asDownVoted(
            post = post,
            downVoted = newValue,
        )
        mvi.updateState { it.copy(post = newPost) }

        mvi.scope?.launch(Dispatchers.IO) {
            try {
                val auth = identityRepository.authToken.value.orEmpty()
                postsRepository.downVote(
                    auth = auth,
                    post = post,
                    downVoted = newValue,
                )
                notificationCenter.getObserver(NotificationCenterContractKeys.PostUpdated)?.also {
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
        val newPost = postsRepository.asSaved(
            post = post,
            saved = newValue,
        )
        mvi.updateState { it.copy(post = newPost) }
        mvi.scope?.launch(Dispatchers.IO) {
            try {
                val auth = identityRepository.authToken.value.orEmpty()
                postsRepository.save(
                    auth = auth,
                    post = post,
                    saved = newValue,
                )
                notificationCenter.getObserver(NotificationCenterContractKeys.PostUpdated)?.also {
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
            postsRepository.delete(id = post.id, auth = auth)
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

private fun processCommentsToGetNestedOrder(
    items: List<CommentModel>,
    ancestorId: String? = null,
): List<CommentModel> {
    val root = Node(null)
    // reconstructs the tree
    for (c in items) {
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