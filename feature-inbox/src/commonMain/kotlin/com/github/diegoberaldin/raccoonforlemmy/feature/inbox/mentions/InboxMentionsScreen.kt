package com.github.diegoberaldin.raccoonforlemmy.feature.inbox.mentions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.theme.Spacing
import com.github.diegoberaldin.raccoonforlemmy.core.architecture.bindToLifecycle
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.communitydetail.CommunityDetailScreen
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.components.InboxMentionCard
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.components.SwipeableCard
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.di.getNavigationCoordinator
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.postdetail.PostDetailScreen
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.userdetail.UserDetailScreen
import com.github.diegoberaldin.raccoonforlemmy.feature.inbox.di.getInboxMentionsViewModel
import com.github.diegoberaldin.raccoonforlemmy.feature.inbox.main.InboxMviModel
import com.github.diegoberaldin.raccoonforlemmy.feature.inbox.main.InboxViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InboxMentionsScreen : Tab {

    var parentModel: InboxViewModel? = null

    override val options: TabOptions
        @Composable get() {
            return TabOptions(1u, "")
        }

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content() {
        val model = rememberScreenModel { getInboxMentionsViewModel() }
        model.bindToLifecycle(key)
        val uiState by model.uiState.collectAsState()
        val parentUiState by (parentModel?.uiState
            ?: MutableStateFlow(InboxMviModel.UiState())).collectAsState()
        val navigator = remember { getNavigationCoordinator().getRootNavigator() }

        LaunchedEffect(parentModel) {
            parentModel?.also { parentModel ->
                parentModel.uiState.onEach {
                    if (it.unreadOnly != model.uiState.value.unreadOnly) {
                        model.reduce(InboxMentionsMviModel.Intent.ChangeUnreadOnly(unread = it.unreadOnly))
                    }
                }.launchIn(this)

                parentModel.effects.onEach {
                    when (it) {
                        InboxMviModel.Effect.Refresh -> {
                            model.reduce(InboxMentionsMviModel.Intent.Refresh)
                        }
                    }
                }.launchIn(this)
            }
        }

        val pullRefreshState = rememberPullRefreshState(uiState.refreshing, {
            model.reduce(InboxMentionsMviModel.Intent.Refresh)
        })
        Box(
            modifier = Modifier.pullRefresh(pullRefreshState),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(uiState.mentions) { mention ->
                    SwipeableCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = {
                            when (it) {
                                DismissValue.DismissedToStart -> MaterialTheme.colorScheme.secondary
                                DismissValue.DismissedToEnd -> MaterialTheme.colorScheme.tertiary
                                else -> Color.Transparent
                            }
                        },
                        onGestureBegin = {
                            model.reduce(InboxMentionsMviModel.Intent.HapticIndication)
                        },
                        onDismissToStart = {
                            model.reduce(
                                InboxMentionsMviModel.Intent.MarkMentionAsRead(
                                    read = true,
                                    mentionId = mention.id,
                                ),
                            )
                        },
                        onDismissToEnd = {
                            model.reduce(
                                InboxMentionsMviModel.Intent.MarkMentionAsRead(
                                    read = false,
                                    mentionId = mention.id,
                                ),
                            )
                        },
                        swipeContent = { direction ->
                            val icon = when (direction) {
                                DismissDirection.StartToEnd -> Icons.Default.MarkChatUnread
                                DismissDirection.EndToStart -> Icons.Default.MarkChatRead
                            }
                            val (iconModifier, iconTint) = when (direction) {
                                DismissDirection.StartToEnd -> {
                                    Modifier.background(
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        shape = CircleShape,
                                    ) to MaterialTheme.colorScheme.tertiary
                                }

                                else -> {
                                    Modifier.background(
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        shape = CircleShape,
                                    ) to MaterialTheme.colorScheme.secondary
                                }
                            }

                            Icon(
                                modifier = iconModifier.padding(Spacing.xs),
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                            )
                        },
                        content = {
                            InboxMentionCard(
                                mention = mention,
                                onOpenPost = { post ->
                                    navigator?.push(
                                        PostDetailScreen(
                                            serialPost = Json.encodeToString(post),
                                        ),
                                    )
                                },
                                onOpenCreator = { user ->
                                    navigator?.push(
                                        UserDetailScreen(
                                            serialUser = Json.encodeToString(user),
                                        ),
                                    )
                                },
                                onOpenCommunity = { community ->
                                    navigator?.push(
                                        CommunityDetailScreen(
                                            serialCommunity = Json.encodeToString(community),
                                        ),
                                    )
                                },
                            )
                        },
                    )
                }
                item {
                    if (!uiState.loading && !uiState.refreshing && uiState.canFetchMore) {
                        model.reduce(InboxMentionsMviModel.Intent.LoadNextPage)
                    }
                    if (uiState.loading && !uiState.refreshing) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.xs),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(25.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(Spacing.xxxl))
                }
            }

            PullRefreshIndicator(
                refreshing = uiState.refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
