package com.github.diegoberaldin.raccoonforlemmy.unit.choosecolor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.theme.Spacing
import com.github.diegoberaldin.raccoonforlemmy.core.commonui.components.BottomSheetHeader
import com.github.diegoberaldin.raccoonforlemmy.core.l10n.messages.LocalStrings
import com.github.diegoberaldin.raccoonforlemmy.core.navigation.di.getNavigationCoordinator
import com.github.diegoberaldin.raccoonforlemmy.core.notifications.NotificationCenterEvent
import com.github.diegoberaldin.raccoonforlemmy.core.notifications.di.getNotificationCenter
import com.github.diegoberaldin.raccoonforlemmy.core.persistence.di.getSettingsRepository
import com.github.diegoberaldin.raccoonforlemmy.core.utils.compose.onClick

class ColorBottomSheet : Screen {
    @Composable
    override fun Content() {
        val navigationCoordinator = remember { getNavigationCoordinator() }
        val notificationCenter = remember { getNotificationCenter() }
        var customPickerDialogOpened by remember { mutableStateOf(false) }
        val settingsRepository = remember { getSettingsRepository() }
        Column(
            modifier =
                Modifier.padding(
                    top = Spacing.s,
                    start = Spacing.s,
                    end = Spacing.s,
                    bottom = Spacing.m,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            BottomSheetHeader(LocalStrings.current.settingsCustomSeedColor)
            val customText = LocalStrings.current.settingsColorCustom
            val values: List<Pair<Color?, String>> =
                listOf(
                    Color(0xFF001F7D) to LocalStrings.current.settingsColorBlue,
                    Color(0xFF36B3B3) to LocalStrings.current.settingsColorAquamarine,
                    Color(0xFF884DFF) to LocalStrings.current.settingsColorPurple,
                    Color(0xFF00B300) to LocalStrings.current.settingsColorGreen,
                    Color(0xFFFF0000) to LocalStrings.current.settingsColorRed,
                    Color(0xFFFF66600) to LocalStrings.current.settingsColorOrange,
                    Color(0x94786818) to LocalStrings.current.settingsColorBanana,
                    Color(0xFFFC0FC0) to LocalStrings.current.settingsColorPink,
                    Color(0xFF303B47) to LocalStrings.current.settingsColorGray,
                    Color(0xFFd7d7d7) to LocalStrings.current.settingsColorWhite,
                    null to customText,
                    null to LocalStrings.current.buttonReset,
                )
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                for (value in values) {
                    val text = value.second
                    val isChooseCustom = text == customText
                    Row(
                        modifier =
                            Modifier
                                .padding(
                                    horizontal = Spacing.s,
                                    vertical = Spacing.s,
                                )
                                .fillMaxWidth()
                                .onClick(
                                    onClick = {
                                        if (!isChooseCustom) {
                                            notificationCenter.send(
                                                NotificationCenterEvent.ChangeColor(
                                                    value.first,
                                                ),
                                            )
                                            navigationCoordinator.hideBottomSheet()
                                        } else {
                                            customPickerDialogOpened = true
                                        }
                                    },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.weight(1f))

                        if (!isChooseCustom) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .background(
                                            color = value.first ?: Color.Transparent,
                                            shape = CircleShape,
                                        ),
                            )
                        } else {
                            Image(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            )
                        }
                    }
                }
            }
        }

        if (customPickerDialogOpened) {
            val current =
                settingsRepository.currentSettings.value.customSeedColor?.let { Color(it) }
            ColorPickerDialog(
                initialValue = current ?: MaterialTheme.colorScheme.primary,
                onClose = {
                    customPickerDialogOpened = false
                },
                onSubmit = { color ->
                    notificationCenter.send(NotificationCenterEvent.ChangeColor(color))
                    navigationCoordinator.hideBottomSheet()
                },
            )
        }
    }
}
