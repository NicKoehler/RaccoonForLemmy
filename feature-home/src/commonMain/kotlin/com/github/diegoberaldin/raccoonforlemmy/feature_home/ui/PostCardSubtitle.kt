package com.github.diegoberaldin.raccoonforlemmy.feature_home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.github.diegoberaldin.raccoonforlemmy.core_appearance.theme.Spacing
import com.github.diegoberaldin.raccoonforlemmy.data.PostModel
import com.seiko.imageloader.rememberImagePainter

@Composable
internal fun PostCardSubtitle(post: PostModel) {
    val communityName = post.community?.name.orEmpty()
    val communityIcon = post.community?.icon.orEmpty()
    val communityHost = post.community?.host.orEmpty()
    val creatorName = post.creator?.name.orEmpty()
    val creatorAvatar = post.creator?.avatar.orEmpty()
    val creatorHost = post.creator?.host.orEmpty()
    val iconSize = 16.dp
    if (communityName.isNotEmpty() || creatorName.isNotEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            if (communityName.isNotEmpty()) {
                if (communityIcon.isNotEmpty()) {
                    val painter = rememberImagePainter(communityIcon)
                    Image(
                        modifier = Modifier.size(iconSize)
                            .clip(RoundedCornerShape(iconSize / 2)),
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                    )
                }
                Text(
                    text = buildString {
                        append(communityName)
                        if (communityHost.isNotEmpty()) {
                            append("@$communityHost")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (creatorName.isNotEmpty()) {
                if (communityName.isNotEmpty()) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (creatorAvatar.isNotEmpty()) {
                    val painter = rememberImagePainter(creatorAvatar)
                    Image(
                        modifier = Modifier.size(iconSize)
                            .clip(RoundedCornerShape(iconSize / 2)),
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                    )
                }
                Text(
                    text = buildString {
                        append(creatorName)
                        if (creatorHost.isNotEmpty() && communityHost != creatorHost) {
                            append("@$creatorHost")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}