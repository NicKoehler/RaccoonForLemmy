package com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data

import com.github.diegoberaldin.raccoonforlemmy.core.utils.JavaSerializable
import com.github.diegoberaldin.raccoonforlemmy.core.utils.looksLikeAnImage

data class PostModel(
    val id: Int = 0,
    val title: String = "",
    val text: String = "",
    val score: Int = 0,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val comments: Int = 0,
    val thumbnailUrl: String? = null,
    val url: String? = null,
    val embedVideoUrl: String? = null,
    val community: CommunityModel? = null,
    val creator: UserModel? = null,
    val saved: Boolean = false,
    val myVote: Int = 0,
    val publishDate: String? = null,
    val nsfw: Boolean = false,
    val read: Boolean = false,
    val crossPosts: List<PostModel> = emptyList(),
) : JavaSerializable


val PostModel.shareUrl: String
    get() = buildString {
        append("https://")
        append(community?.host)
        append("/post/")
        append(id)
    }

val PostModel.imageUrl: String
    get() = thumbnailUrl?.takeIf { it.isNotEmpty() } ?: run {
        url?.takeIf { it.looksLikeAnImage }
    }.orEmpty()
