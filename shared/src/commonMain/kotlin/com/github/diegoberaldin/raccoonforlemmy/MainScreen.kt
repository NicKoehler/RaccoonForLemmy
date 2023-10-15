package com.github.diegoberaldin.raccoonforlemmy

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.material.BottomAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.di.getThemeRepository
import com.github.diegoberaldin.raccoonforlemmy.core.architecture.bindToLifecycle
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.communitydetail.CommunityDetailScreen
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.components.handleUrl
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.di.getDrawerCoordinator
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.di.getNavigationCoordinator
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.drawer.DrawerEvent
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.drawer.ModalDrawerContent
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.saveditems.SavedItemsScreen
import com.github.diegoberaldin.raccoonforlemmy.core.persistence.di.getSettingsRepository
import com.github.diegoberaldin.raccoonforlemmy.di.getMainViewModel
import com.github.diegoberaldin.raccoonforlemmy.feature.home.ui.HomeTab
import com.github.diegoberaldin.raccoonforlemmy.feature.inbox.ui.InboxTab
import com.github.diegoberaldin.raccoonforlemmy.feature.profile.ui.ProfileTab
import com.github.diegoberaldin.raccoonforlemmy.feature.search.managesubscriptions.ManageSubscriptionsScreen
import com.github.diegoberaldin.raccoonforlemmy.feature.search.multicommunity.detail.MultiCommunityScreen
import com.github.diegoberaldin.raccoonforlemmy.feature.search.ui.SearchTab
import com.github.diegoberaldin.raccoonforlemmy.feature.settings.ui.SettingsTab
import com.github.diegoberaldin.raccoonforlemmy.ui.navigation.TabNavigationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal class MainScreen : Screen {

    @Composable
    override fun Content() {
        val themeRepository = remember { getThemeRepository() }
        var bottomBarHeightPx by remember { mutableStateOf(0f) }
        val navigationCoordinator = remember { getNavigationCoordinator() }
        val drawerCoordinator = remember { getDrawerCoordinator() }
        val navigator = remember { navigationCoordinator.getRootNavigator() }
        val model = rememberScreenModel { getMainViewModel() }
        model.bindToLifecycle(key)
        val uiState by model.uiState.collectAsState()
        val uiFontScale by themeRepository.uiFontScale.collectAsState()
        val uriHandler = LocalUriHandler.current
        val settingsRepository = remember { getSettingsRepository() }
        val settings by settingsRepository.currentSettings.collectAsState()

        LaunchedEffect(model) {
            model.effects.onEach {
                when (it) {
                    is MainScreenMviModel.Effect.UnreadItemsDetected -> {
                        navigationCoordinator.setInboxUnread(it.value)
                    }
                }
            }.launchIn(this)
        }

        LaunchedEffect(navigationCoordinator) {
            val scrollConnection = object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    val newOffset =
                        (uiState.bottomBarOffsetHeightPx + delta).coerceIn(
                            -bottomBarHeightPx,
                            0f
                        )
                    model.reduce(MainScreenMviModel.Intent.SetBottomBarOffsetHeightPx(newOffset))
                    return Offset.Zero
                }
            }
            navigationCoordinator.apply {
                setBottomBarScrollConnection(scrollConnection)
                setCurrentSection(HomeTab)
            }
        }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        LaunchedEffect(drawerCoordinator) {
            drawerCoordinator.toggleEvents.onEach { evt ->
                when (evt) {
                    DrawerEvent.Toggled -> {
                        drawerState.apply {
                            if (isClosed) open() else close()
                        }
                    }

                    is DrawerEvent.OpenCommunity -> {
                        navigator?.push(CommunityDetailScreen(evt.community))
                    }

                    is DrawerEvent.OpenMultiCommunity -> {
                        navigator?.push(MultiCommunityScreen(evt.community))
                    }

                    DrawerEvent.ManageSubscriptions -> {
                        navigator?.push(ManageSubscriptionsScreen())
                    }

                    DrawerEvent.OpenBookmarks -> {
                        navigator?.push(SavedItemsScreen())
                    }
                }
            }.launchIn(this)
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent =
            {
                ModalDrawerSheet {
                    TabNavigator(ModalDrawerContent)
                }
            }
        ) {
            TabNavigator(HomeTab) {
                Scaffold(
                    content = {
                        CurrentTab()
                    },
                    bottomBar = {
                        CompositionLocalProvider(
                            LocalDensity provides Density(
                                density = LocalDensity.current.density,
                                fontScale = uiFontScale,
                            ),
                        ) {
                            val titleVisible by themeRepository.navItemTitles.collectAsState()
                            var uiFontSizeWorkaround by remember { mutableStateOf(true) }
                            LaunchedEffect(themeRepository) {
                                themeRepository.uiFontScale.drop(1).onEach {
                                    uiFontSizeWorkaround = false
                                    delay(50)
                                    uiFontSizeWorkaround = true
                                }.launchIn(this)
                            }
                            if (uiFontSizeWorkaround) {
                                BottomAppBar(
                                    modifier = Modifier
                                        .onGloballyPositioned {
                                            bottomBarHeightPx = it.size.toSize().height
                                        }
                                        .offset {
                                            IntOffset(
                                                x = 0,
                                                y = -uiState.bottomBarOffsetHeightPx.roundToInt()
                                            )
                                        },
                                    contentPadding = PaddingValues(0.dp),
                                    backgroundColor = MaterialTheme.colorScheme.background,
                                ) {
                                    TabNavigationItem(HomeTab, withText = titleVisible)
                                    TabNavigationItem(SearchTab, withText = titleVisible)
                                    TabNavigationItem(ProfileTab, withText = titleVisible)
                                    TabNavigationItem(InboxTab, withText = titleVisible)
                                    TabNavigationItem(SettingsTab, withText = titleVisible)
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}