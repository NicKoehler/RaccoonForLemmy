package com.github.diegoberaldin.raccoonforlemmy

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.bottomSheet.BottomSheetNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.data.UiTheme
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.data.toInt
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.data.toPostLayout
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.data.toUiFontFamily
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.data.toUiTheme
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.di.getThemeRepository
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.theme.AppTheme
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.theme.CornerSize
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.communitydetail.CommunityDetailScreen
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.components.getCommunityFromUrl
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.components.getPostFromUrl
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.components.getUserFromUrl
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.drawer.ModalDrawerContent
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.postdetail.PostDetailScreen
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.saveditems.SavedItemsScreen
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.userdetail.UserDetailScreen
import com.github.diegoberaldin.raccoonforlemmy.core.navigation.DrawerEvent
import com.github.diegoberaldin.raccoonforlemmy.core.navigation.di.getDrawerCoordinator
import com.github.diegoberaldin.raccoonforlemmy.core.navigation.di.getNavigationCoordinator
import com.github.diegoberaldin.raccoonforlemmy.core.persistence.di.getAccountRepository
import com.github.diegoberaldin.raccoonforlemmy.core.persistence.di.getSettingsRepository
import com.github.diegoberaldin.raccoonforlemmy.core.utils.debug.getCrashReportConfiguration
import com.github.diegoberaldin.raccoonforlemmy.core.utils.debug.getCrashReportSender
import com.github.diegoberaldin.raccoonforlemmy.core.utils.toLanguageDirection
import com.github.diegoberaldin.raccoonforlemmy.domain.identity.di.getApiConfigurationRepository
import com.github.diegoberaldin.raccoonforlemmy.feature.search.managesubscriptions.ManageSubscriptionsScreen
import com.github.diegoberaldin.raccoonforlemmy.feature.search.multicommunity.detail.MultiCommunityScreen
import com.github.diegoberaldin.raccoonforlemmy.resources.MR
import com.github.diegoberaldin.raccoonforlemmy.resources.di.getLanguageRepository
import dev.icerock.moko.resources.compose.stringResource
import dev.icerock.moko.resources.desc.StringDesc
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class, FlowPreview::class)
@Composable
fun App(onLoadingFinished: () -> Unit = {}) {
    val accountRepository = remember { getAccountRepository() }
    val settingsRepository = remember { getSettingsRepository() }
    val settings by settingsRepository.currentSettings.collectAsState()
    var hasBeenInitialized by remember { mutableStateOf(false) }
    val apiConfigurationRepository = remember { getApiConfigurationRepository() }
    val crashReportSender = remember { getCrashReportSender() }
    val crashReportConfiguration = remember { getCrashReportConfiguration() }
    val themeRepository = remember { getThemeRepository() }
    val defaultTheme = if (isSystemInDarkTheme()) {
        UiTheme.Dark
    } else {
        UiTheme.Light
    }.toInt()
    val defaultLocale = stringResource(MR.strings.lang)
    val languageRepository = remember { getLanguageRepository() }
    val locale by derivedStateOf { settings.locale }
    val currentTheme by themeRepository.uiTheme.collectAsState()
    val useDynamicColors by themeRepository.dynamicColors.collectAsState()
    val fontScale by themeRepository.contentFontScale.collectAsState()
    val uiFontScale by themeRepository.uiFontScale.collectAsState()
    val navigationCoordinator = remember { getNavigationCoordinator() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerCoordinator = remember { getDrawerCoordinator() }
    val drawerGesturesEnabled by drawerCoordinator.gesturesEnabled.collectAsState()

    LaunchedEffect(Unit) {
        val accountId = accountRepository.getActive()?.id
        val currentSettings = settingsRepository.getSettings(accountId)
        settingsRepository.changeCurrentSettings(currentSettings)
        val lastActiveAccount = accountRepository.getActive()
        val lastInstance = lastActiveAccount?.instance?.takeIf { it.isNotEmpty() }
        if (lastInstance != null) {
            apiConfigurationRepository.changeInstance(lastInstance)
        }
        crashReportSender.initialize()
        crashReportSender.setEnabled(crashReportConfiguration.isEnabled())

        with(themeRepository) {
            changeUiTheme((currentSettings.theme ?: defaultTheme).toUiTheme())
            changeNavItemTitles(currentSettings.navigationTitlesVisible)
            changeDynamicColors(currentSettings.dynamicColors)
            changeCustomSeedColor(currentSettings.customSeedColor?.let { Color(it) })
            changePostLayout(currentSettings.postLayout.toPostLayout())
            changeContentFontScale(currentSettings.contentFontScale)
            changeUiFontScale(currentSettings.uiFontScale)
            changeUiFontFamily(currentSettings.uiFontFamily.toUiFontFamily())

            with(themeRepository) {
                changeUpvoteColor(currentSettings.upvoteColor?.let { Color(it) })
                changeDownvoteColor(currentSettings.downvoteColor?.let { Color(it) })
            }
        }

        hasBeenInitialized = true
        launch {
            delay(50)
            onLoadingFinished()
        }
    }

    LaunchedEffect(locale) {
        languageRepository.changeLanguage(locale ?: defaultLocale)
    }
    val scope = rememberCoroutineScope()
    languageRepository.currentLanguage.onEach { lang ->
        StringDesc.localeType = StringDesc.LocaleType.Custom(lang)
    }.launchIn(scope)

    LaunchedEffect(settings) {
        with(themeRepository) {
            changeUiTheme(settings.theme.toUiTheme())
            changeNavItemTitles(settings.navigationTitlesVisible)
            changeDynamicColors(settings.dynamicColors)
            changeCustomSeedColor(settings.customSeedColor?.let { Color(it) })
            changePostLayout(settings.postLayout.toPostLayout())
            changeContentFontScale(settings.contentFontScale)
            changeUiFontScale(settings.uiFontScale)
            changeUiFontFamily(settings.uiFontFamily.toUiFontFamily())

            with(themeRepository) {
                changeUpvoteColor(settings.upvoteColor?.let { Color(it) })
                changeDownvoteColor(settings.downvoteColor?.let { Color(it) })
            }
        }
    }

    LaunchedEffect(navigationCoordinator) {
        navigationCoordinator.deepLinkUrl.debounce(750).onEach { url ->
            val community = getCommunityFromUrl(url)
            val user = getUserFromUrl(url)
            val postAndInstance = getPostFromUrl(url)
            val newScreen = when {
                community != null -> {
                    CommunityDetailScreen(
                        community = community,
                        otherInstance = community.host,
                    )
                }

                user != null -> {
                    UserDetailScreen(
                        user = user,
                        otherInstance = user.host,
                    )
                }

                postAndInstance != null -> {
                    val (post, otherInstance) = postAndInstance
                    PostDetailScreen(
                        post = post,
                        otherInstance = otherInstance,
                    )
                }

                else -> null
            }
            if (newScreen != null) {
                navigationCoordinator.pushScreen(newScreen)
            }
        }.launchIn(this)
    }

    LaunchedEffect(drawerCoordinator) {
        drawerCoordinator.toggleEvents.onEach { evt ->
            when (evt) {
                DrawerEvent.Toggled -> {
                    drawerState.apply {
                        launch {
                            if (isClosed) {
                                open()
                            } else {
                                close()
                            }
                        }
                    }
                }

                is DrawerEvent.OpenCommunity -> {
                    navigationCoordinator.pushScreen(CommunityDetailScreen(evt.community))
                }

                is DrawerEvent.OpenMultiCommunity -> {
                    navigationCoordinator.pushScreen(MultiCommunityScreen(evt.community))
                }

                DrawerEvent.ManageSubscriptions -> {
                    navigationCoordinator.pushScreen(ManageSubscriptionsScreen())
                }

                DrawerEvent.OpenBookmarks -> {
                    navigationCoordinator.pushScreen(SavedItemsScreen())
                }
            }
        }.launchIn(this)
    }

    AppTheme(
        theme = currentTheme,
        contentFontScale = fontScale,
        useDynamicColors = useDynamicColors,
    ) {
        val lang by languageRepository.currentLanguage.collectAsState()
        LaunchedEffect(lang) {}
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = LocalDensity.current.density,
                fontScale = uiFontScale,
            ),
            LocalLayoutDirection provides lang.toLanguageDirection(),
        ) {
            BottomSheetNavigator(
                sheetShape = RoundedCornerShape(
                    topStart = CornerSize.xl,
                    topEnd = CornerSize.xl
                ),
                sheetBackgroundColor = MaterialTheme.colorScheme.background,
            ) { bottomNavigator ->
                navigationCoordinator.setBottomNavigator(bottomNavigator)

                Navigator(
                    screen = MainScreen,
                    onBackPressed = {
                        val callback = navigationCoordinator.getCanGoBackCallback()
                        callback?.let { it() } ?: true
                    },
                ) { navigator ->
                    LaunchedEffect(Unit) {
                        navigationCoordinator.setRootNavigator(navigator)
                    }
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = drawerGesturesEnabled,
                        drawerContent = {
                            ModalDrawerSheet {
                                TabNavigator(ModalDrawerContent)
                            }
                        },
                    ) {
                        if (hasBeenInitialized) {
                            SlideTransition(navigator)
                        }
                    }
                }
            }
        }
    }
}
