package com.github.diegoberaldin.raccoonforlemmy.core.commonui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Padding
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.github.diegoberaldin.raccoonforlemmy.core.appearance.theme.Spacing
import com.github.diegoberaldin.raccoonforlemmy.core.utils.DateTime
import com.github.diegoberaldin.raccoonforlemmy.core.utils.onClick
import com.github.diegoberaldin.raccoonforlemmy.core.utils.toLocalDp
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.UserModel
import com.github.diegoberaldin.raccoonforlemmy.resources.MR
import dev.icerock.moko.resources.compose.stringResource

@Composable
fun UserHeader(
    user: UserModel,
    onOpenBookmarks: (() -> Unit)? = null,
    options: List<String> = emptyList(),
    onOptionSelected: ((Int) -> Unit)? = null,
) {
    Box(
        modifier = Modifier.padding(Spacing.xs),
    ) {
        // banner
        val banner = user.banner.orEmpty()
        if (banner.isNotEmpty()) {
            CustomImage(
                modifier = Modifier.fillMaxWidth().aspectRatio(4.5f),
                url = banner,
                quality = FilterQuality.Low,
                contentScale = ContentScale.FillBounds,
                contentDescription = null,
            )
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            // options menu
            if (options.isNotEmpty()) {
                var optionsExpanded by remember { mutableStateOf(false) }
                var optionsOffset by remember { mutableStateOf(Offset.Zero) }
                Icon(
                    modifier = Modifier.onGloballyPositioned {
                        optionsOffset = it.positionInParent()
                    }.onClick {
                        optionsExpanded = true
                    },
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = null,
                )
                CustomDropDown(
                    expanded = optionsExpanded,
                    onDismiss = {
                        optionsExpanded = false
                    },
                    offset = DpOffset(
                        x = optionsOffset.x.toLocalDp(),
                        y = optionsOffset.y.toLocalDp(),
                    ),
                ) {
                    options.forEachIndexed { idx, option ->
                        Text(
                            modifier = Modifier.padding(
                                horizontal = Spacing.m,
                                vertical = Spacing.xs,
                            ).onClick {
                                optionsExpanded = false
                                onOptionSelected?.invoke(idx)
                            },
                            text = option,
                        )
                    }
                }
            }

            // open bookmarks button
            if (onOpenBookmarks != null) {
                Icon(
                    modifier = Modifier.onClick {
                        onOpenBookmarks.invoke()
                    },
                    imageVector = Icons.Outlined.Bookmarks,
                    contentDescription = null,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            // avatar
            val userAvatar = user.avatar.orEmpty()
            val avatarSize = 60.dp
            if (userAvatar.isNotEmpty()) {
                CustomImage(
                    modifier = Modifier
                        .padding(Spacing.xxxs)
                        .size(avatarSize)
                        .clip(RoundedCornerShape(avatarSize / 2)),
                    url = userAvatar,
                    quality = FilterQuality.Low,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(Spacing.xxxs)
                        .size(avatarSize)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(avatarSize / 2),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = user.name.firstOrNull()?.toString()
                            .orEmpty()
                            .uppercase(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            // textual data
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    text = buildString {
                        if (user.displayName.isNotEmpty()) {
                            append(user.displayName)
                        } else {
                            append(user.name)
                        }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = buildString {
                        append(user.name)
                        append("@")
                        append(user.host)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                // stats and age
                val iconSize = 22.dp
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val postScore = user.score?.postScore
                    val commentScore = user.score?.commentScore
                    if (postScore != null) {
                        Icon(
                            modifier = Modifier.size(iconSize),
                            imageVector = Icons.Default.Padding,
                            contentDescription = null
                        )
                        Text(
                            text = postScore.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    if (commentScore != null) {
                        if (postScore != null) {
                            Spacer(modifier = Modifier.width(Spacing.xxxs))
                        }
                        Icon(
                            modifier = Modifier.size(iconSize),
                            imageVector = Icons.Default.Reply,
                            contentDescription = null
                        )
                        Text(
                            text = commentScore.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }

                    if (user.accountAge.isNotEmpty()) {
                        if (postScore != null || commentScore != null) {
                            Spacer(modifier = Modifier.width(Spacing.xxxs))
                        }
                        Icon(
                            modifier = Modifier.size(iconSize),
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null
                        )
                        Text(
                            text = user.accountAge.let {
                                when {
                                    !it.endsWith("Z") -> {
                                        DateTime.getPrettyDate(
                                            iso8601Timestamp = it + "Z",
                                            yearLabel = stringResource(MR.strings.profile_year_short),
                                            monthLabel = stringResource(MR.strings.profile_month_short),
                                            dayLabel = stringResource(MR.strings.profile_day_short),
                                            hourLabel = stringResource(MR.strings.post_hour_short),
                                            minuteLabel = stringResource(MR.strings.post_minute_short),
                                            secondLabel = stringResource(MR.strings.post_second_short),
                                        )
                                    }

                                    else -> {
                                        DateTime.getPrettyDate(
                                            iso8601Timestamp = it,
                                            yearLabel = stringResource(MR.strings.profile_year_short),
                                            monthLabel = stringResource(MR.strings.profile_month_short),
                                            dayLabel = stringResource(MR.strings.profile_day_short),
                                            hourLabel = stringResource(MR.strings.post_hour_short),
                                            minuteLabel = stringResource(MR.strings.post_minute_short),
                                            secondLabel = stringResource(MR.strings.post_second_short),
                                        )
                                    }
                                }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}
