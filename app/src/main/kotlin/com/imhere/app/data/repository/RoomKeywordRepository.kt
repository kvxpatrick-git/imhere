package com.imhere.app.data.repository

import com.imhere.app.data.local.FieldCipher
import com.imhere.app.data.local.KeywordDao
import com.imhere.app.data.local.KeywordEntity
import com.imhere.core.domain.port.KeywordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomKeywordRepository(
    private val dao: KeywordDao
) : KeywordRepository {
    override suspend fun get(): List<String> = dao.listAll().map { FieldCipher.reveal(it.keywordProtected) }

    override suspend fun saveKeywords(keywords: List<String>) {
        val normalized = keywords.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        dao.replaceAll(normalized.map { KeywordEntity(keywordProtected = FieldCipher.protect(it)) })
    }

    override val keywordsFlow: Flow<List<String>> = dao.observeAll().map { entities ->
        entities.map { FieldCipher.reveal(it.keywordProtected) }
    }
}
