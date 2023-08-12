package com.github.diegoberaldin.raccoonforlemmy.core.api.service

import com.github.diegoberaldin.raccoonforlemmy.core.api.dto.BlockCommunityForm
import com.github.diegoberaldin.raccoonforlemmy.core.api.dto.BlockCommunityResponse
import com.github.diegoberaldin.raccoonforlemmy.core.api.dto.CommunityResponse
import com.github.diegoberaldin.raccoonforlemmy.core.api.dto.FollowCommunityForm
import com.github.diegoberaldin.raccoonforlemmy.core.api.dto.GetCommunityResponse
import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query

interface CommunityService {

    @GET("community")
    suspend fun get(
        @Query("auth") auth: String? = null,
        @Query("id") id: Int? = null,
        @Query("name") name: String? = null,
    ): Response<GetCommunityResponse>

    @POST("community/follow")
    @Headers("Content-Type: application/json")
    suspend fun follow(@Body form: FollowCommunityForm): Response<CommunityResponse>

    @POST("community/block")
    @Headers("Content-Type: application/json")
    suspend fun block(@Body form: BlockCommunityForm): Response<BlockCommunityResponse>
}
