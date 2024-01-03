package com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.repository

import com.github.diegoberaldin.raccoonforlemmy.core.api.dto.CreatePrivateMessageForm
import com.github.diegoberaldin.raccoonforlemmy.core.api.dto.DeletePrivateMessageForm
import com.github.diegoberaldin.raccoonforlemmy.core.api.dto.EditPrivateMessageForm
import com.github.diegoberaldin.raccoonforlemmy.core.api.dto.MarkPrivateMessageAsReadForm
import com.github.diegoberaldin.raccoonforlemmy.core.api.provider.ServiceProvider
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.data.PrivateMessageModel
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.repository.utils.toAuthHeader
import com.github.diegoberaldin.raccoonforlemmy.domain.lemmy.repository.utils.toModel

internal class DefaultPrivateMessageRepository(
    private val services: ServiceProvider,
) : PrivateMessageRepository {

    override suspend fun getAll(
        auth: String?,
        creatorId: Int?,
        page: Int,
        limit: Int,
        unreadOnly: Boolean,
    ): List<PrivateMessageModel>? = runCatching {
        val response = services.privateMessages.getAll(
            authHeader = auth.toAuthHeader(),
            auth = auth,
            creatorId = creatorId,
            limit = limit,
            page = page,
            unreadOnly = unreadOnly,
        )
        val dto = response.body() ?: return@runCatching emptyList()
        dto.privateMessages.map { it.toModel() }
    }.getOrNull()

    override suspend fun create(
        message: String,
        auth: String?,
        recipientId: Int,
    ): PrivateMessageModel? = runCatching {
        val data = CreatePrivateMessageForm(
            content = message,
            auth = auth.orEmpty(),
            recipientId = recipientId,
        )
        val dto = services.privateMessages.create(
            authHeader = auth.toAuthHeader(),
            form = data,
        ).body()
        dto?.privateMessageView?.toModel()
    }.getOrNull()

    override suspend fun edit(
        messageId: Int,
        message: String,
        auth: String?,
    ): PrivateMessageModel? = runCatching {
        val data = EditPrivateMessageForm(
            content = message,
            auth = auth.orEmpty(),
            privateMessageId = messageId,
        )
        val dto = services.privateMessages.edit(
            authHeader = auth.toAuthHeader(),
            form = data,
        ).body()
        dto?.privateMessageView?.toModel()
    }.getOrNull()

    override suspend fun markAsRead(
        messageId: Int,
        auth: String?,
        read: Boolean,
    ): PrivateMessageModel? = runCatching {
        val data = MarkPrivateMessageAsReadForm(
            privateMessageId = messageId,
            auth = auth.orEmpty(),
            read = read,
        )
        val dto = services.privateMessages.markAsRead(
            authHeader = auth.toAuthHeader(),
            form = data,
        )
        return dto.body()?.privateMessageView?.toModel()
    }.getOrNull()

    override suspend fun delete(messageId: Int, auth: String?) = runCatching {
        val data = DeletePrivateMessageForm(
            auth = auth.orEmpty(),
            privateMessageId = messageId,
            deleted = true,
        )
        services.privateMessages.delete(
            authHeader = auth.toAuthHeader(),
            form = data,
        )
        Unit
    }.getOrDefault(Unit)
}
