package com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data

import kotlinx.serialization.Serializable

@Serializable
data class PostModel(
    val id: Int = 0,
    val title: String = "",
    val text: String = "",
    val score: Int = 0,
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
)
