package com.github.diegoberaldin.raccoonforlemmy.unit.createpost

import cafe.adriel.voyager.core.model.screenModelScope
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.repository.ThemeRepository
import com.github.diegoberaldin.raccoonforlemmy.core.architecture.DefaultMviModel
import com.github.diegoberaldin.raccoonforlemmy.core.notifications.NotificationCenter
import com.github.diegoberaldin.raccoonforlemmy.core.notifications.NotificationCenterEvent
import com.github.diegoberaldin.raccoonforlemmy.core.persistence.data.DraftModel
import com.github.diegoberaldin.raccoonforlemmy.core.persistence.data.DraftType
import com.github.diegoberaldin.raccoonforlemmy.core.persistence.repository.AccountRepository
import com.github.diegoberaldin.raccoonforlemmy.core.persistence.repository.DraftRepository
import com.github.diegoberaldin.raccoonforlemmy.core.persistence.repository.SettingsRepository
import com.github.diegoberaldin.raccoonforlemmy.core.utils.ValidationError
import com.github.diegoberaldin.raccoonforlemmy.core.utils.datetime.epochMillis
import com.github.diegoberaldin.raccoonforlemmy.core.utils.isValidUrl
import com.github.diegoberaldin.raccoonforlemmy.domain.identity.repository.IdentityRepository
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.CommunityModel
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.readableName
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.repository.CommunityRepository
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.repository.LemmyItemCache
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.repository.PostRepository
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.repository.SiteRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CreatePostViewModel(
    private val editedPostId: Long?,
    private val crossPostId: Long?,
    private val draftId: Long?,
    private val identityRepository: IdentityRepository,
    private val postRepository: PostRepository,
    private val siteRepository: SiteRepository,
    private val themeRepository: ThemeRepository,
    private val settingsRepository: SettingsRepository,
    private val itemCache: LemmyItemCache,
    private val communityRepository: CommunityRepository,
    private val accountRepository: AccountRepository,
    private val draftRepository: DraftRepository,
    private val notificationCenter: NotificationCenter,
) : CreatePostMviModel,
    DefaultMviModel<CreatePostMviModel.Intent, CreatePostMviModel.UiState, CreatePostMviModel.Effect>(
        initialState = CreatePostMviModel.UiState(),
    ) {
    init {
        screenModelScope.launch {
            val editedPost =
                editedPostId?.let {
                    itemCache.getPost(it)
                }
            val crossPost =
                crossPostId?.let {
                    itemCache.getPost(it)
                }
            updateState { it.copy(editedPost = editedPost, crossPost = crossPost) }

            themeRepository.postLayout.onEach { layout ->
                updateState { it.copy(postLayout = layout) }
            }.launchIn(this)
            settingsRepository.currentSettings.onEach { settings ->
                updateState {
                    it.copy(
                        voteFormat = settings.voteFormat,
                        autoLoadImages = settings.autoLoadImages,
                        preferNicknames = settings.preferUserNicknames,
                        fullHeightImages = settings.fullHeightImages,
                        fullWidthImages = settings.fullWidthImages,
                        showScores = settings.showScores,
                        currentLanguageId = settings.defaultLanguageId,
                    )
                }
            }.launchIn(this)

            if (uiState.value.currentUser.isEmpty()) {
                val auth = identityRepository.authToken.value.orEmpty()
                val currentUser = siteRepository.getCurrentUser(auth)
                val languages = siteRepository.getLanguages(auth)
                val downVoteEnabled = siteRepository.isDownVoteEnabled(auth)
                if (currentUser != null) {
                    updateState {
                        it.copy(
                            currentUser = currentUser.name,
                            currentInstance = currentUser.host,
                            availableLanguages = languages,
                            downVoteEnabled = downVoteEnabled,
                        )
                    }
                }
            }
        }
    }

    override fun reduce(intent: CreatePostMviModel.Intent) {
        when (intent) {
            is CreatePostMviModel.Intent.SetCommunity -> {
                updateCommunity(intent.value)
            }

            is CreatePostMviModel.Intent.SetTitle -> {
                screenModelScope.launch {
                    updateState {
                        it.copy(title = intent.value)
                    }
                }
            }

            is CreatePostMviModel.Intent.ChangeNsfw -> {
                screenModelScope.launch {
                    updateState {
                        it.copy(nsfw = intent.value)
                    }
                }
            }

            is CreatePostMviModel.Intent.SetUrl -> {
                screenModelScope.launch {
                    updateState {
                        it.copy(url = intent.value)
                    }
                }
            }

            is CreatePostMviModel.Intent.ImageSelected -> {
                loadImageAndObtainUrl(intent.value)
            }

            is CreatePostMviModel.Intent.InsertImageInBody -> {
                loadImageAndAppendUrlInBody(intent.value)
            }

            is CreatePostMviModel.Intent.ChangeSection ->
                screenModelScope.launch {
                    updateState {
                        it.copy(section = intent.value)
                    }
                }

            is CreatePostMviModel.Intent.ChangeLanguage ->
                screenModelScope.launch {
                    updateState {
                        it.copy(currentLanguageId = intent.value)
                    }
                }
            is CreatePostMviModel.Intent.ChangeBodyValue ->
                screenModelScope.launch {
                    updateState {
                        it.copy(bodyValue = intent.value)
                    }
                }

            CreatePostMviModel.Intent.Send -> submit()

            CreatePostMviModel.Intent.SaveDraft -> saveDraft()
            CreatePostMviModel.Intent.AutoFillTitle -> autofillTitle()
        }
    }

    private fun updateCommunity(community: CommunityModel) {
        val preferNicknames = uiState.value.preferNicknames
        val communityId = community.id
        val name = community.readableName(preferNicknames)
        val auth = identityRepository.authToken.value.orEmpty()
        screenModelScope.launch {
            val communityInfo =
                name.ifEmpty {
                    val remoteCommunity = communityRepository.get(auth = auth, id = communityId)
                    remoteCommunity?.name.orEmpty()
                }
            updateState {
                it.copy(
                    communityId = communityId,
                    communityInfo = communityInfo,
                )
            }
        }
    }

    private fun loadImageAndObtainUrl(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            return
        }
        screenModelScope.launch {
            updateState { it.copy(loading = true) }
            val auth = identityRepository.authToken.value.orEmpty()
            val url = postRepository.uploadImage(auth, bytes)
            updateState {
                it.copy(
                    url = url.orEmpty(),
                    loading = false,
                )
            }
        }
    }

    private fun loadImageAndAppendUrlInBody(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            return
        }
        screenModelScope.launch {
            updateState { it.copy(loading = true) }
            val auth = identityRepository.authToken.value.orEmpty()
            val url = postRepository.uploadImage(auth, bytes)
            if (url != null) {
                val newValue =
                    uiState.value.bodyValue.let {
                        it.copy(text = it.text + "\n![]($url)")
                    }
                updateState {
                    it.copy(
                        loading = false,
                        bodyValue = newValue,
                    )
                }
            } else {
                updateState {
                    it.copy(loading = false)
                }
            }
        }
    }

    private fun submit() {
        val currentState = uiState.value
        if (currentState.loading) {
            return
        }

        screenModelScope.launch {
            updateState {
                it.copy(
                    titleError = null,
                    urlError = null,
                    bodyError = null,
                )
            }
        }

        val communityId = currentState.communityId
        val title = currentState.title.trim()
        val url = currentState.url.takeIf { it.isNotEmpty() }?.trim()
        val body = currentState.bodyValue.text.takeIf { it.isNotBlank() }?.trim()
        val nsfw = currentState.nsfw
        val languageId = currentState.currentLanguageId
        var valid = true
        if (title.isEmpty()) {
            screenModelScope.launch {
                updateState {
                    it.copy(titleError = ValidationError.MissingField)
                }
            }
            valid = false
        }
        if (!url.isNullOrEmpty() && !url.isValidUrl()) {
            screenModelScope.launch {
                updateState {
                    it.copy(urlError = ValidationError.InvalidField)
                }
            }
            valid = false
        }
        if (communityId == null) {
            screenModelScope.launch {
                updateState {
                    it.copy(communityError = ValidationError.MissingField)
                }
            }
            valid = false
        }

        if (!valid) {
            return
        }

        screenModelScope.launch {
            updateState { it.copy(loading = true) }
            try {
                val auth = identityRepository.authToken.value.orEmpty()
                when {
                    editedPostId != null -> {
                        postRepository.edit(
                            postId = editedPostId,
                            title = title,
                            body = body,
                            url = url,
                            nsfw = nsfw,
                            languageId = languageId,
                            auth = auth,
                        )
                    }

                    communityId != null -> {
                        postRepository.create(
                            communityId = communityId,
                            title = title,
                            body = body,
                            url = url,
                            nsfw = nsfw,
                            languageId = languageId,
                            auth = auth,
                        )
                    }
                }
                if (draftId != null) {
                    deleteDraft()
                }
                emitEffect(CreatePostMviModel.Effect.Success)
            } catch (e: Throwable) {
                val message = e.message
                emitEffect(CreatePostMviModel.Effect.Failure(message))
            } finally {
                updateState { it.copy(loading = false) }
            }
        }
    }

    private fun saveDraft() {
        val currentState = uiState.value
        if (currentState.loading) {
            return
        }
        val communityId = currentState.communityId
        val title = currentState.title
        val url = currentState.url.takeIf { it.isNotEmpty() }?.trim()
        val body = currentState.bodyValue.text
        val nsfw = currentState.nsfw
        val languageId = currentState.currentLanguageId

        screenModelScope.launch {
            val accountId = accountRepository.getActive()?.id ?: return@launch
            updateState { it.copy(loading = true) }
            val auth = identityRepository.authToken.value
            val community =
                communityId?.let {
                    communityRepository.get(auth = auth, id = communityId)
                }
            val draft =
                DraftModel(
                    id = draftId,
                    type = DraftType.Post,
                    body = body,
                    title = title,
                    url = url,
                    nsfw = nsfw,
                    communityId = communityId,
                    languageId = languageId,
                    date = epochMillis(),
                    reference = community?.name,
                )
            if (draftId == null) {
                draftRepository.create(
                    model = draft,
                    accountId = accountId,
                )
            } else {
                draftRepository.update(draft)
            }
            updateState { it.copy(loading = false) }
            emitEffect(CreatePostMviModel.Effect.DraftSaved)
        }
    }

    private suspend fun deleteDraft() {
        draftId?.also { id ->
            draftRepository.delete(id)
            notificationCenter.send(NotificationCenterEvent.DraftDeleted)
        }
    }

    private fun autofillTitle() {
        val url = uiState.value.url.takeUnless { it.isBlank() } ?: return
        screenModelScope.launch {
            updateState { it.copy(loading = true) }
            val metadata = siteRepository.getMetadata(url)
            val suggestedTitle = metadata?.title.takeUnless { it.isNullOrBlank() }
            updateState { it.copy(loading = false) }
            if (suggestedTitle == null) {
                emitEffect(CreatePostMviModel.Effect.AutoFillFailed)
            } else {
                updateState { it.copy(title = suggestedTitle) }
            }
        }
    }
}
