package com.github.diegoberaldin.raccoonforlemmy.core.persistence.data

data class MultiCommunityModel(
    val id: Long? = null,
    val name: String = "",
    val communityIds: List<Long> = emptyList(),
    val icon: String? = null,
)
