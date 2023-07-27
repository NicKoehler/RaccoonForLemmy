package com.github.diegoberaldin.raccoonforlemmy.feature_home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.github.diegoberaldin.raccoonforlemmy.data.PostModel
import com.seiko.imageloader.rememberImagePainter

@Composable
internal fun PostCardImage(post: PostModel) {
    val imageUrl = post.thumbnailUrl
    if (!imageUrl.isNullOrEmpty()) {
        val painter = rememberImagePainter(imageUrl)
        Image(
            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
        )
    }
}