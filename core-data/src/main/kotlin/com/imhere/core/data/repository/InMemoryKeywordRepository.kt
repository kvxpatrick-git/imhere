package com.imhere.core.data.repository

import com.imhere.core.data.security.LocalObfuscator
import com.imhere.core.domain.port.KeywordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryKeywordRepository : KeywordRepository {
    private val lock = Mutex()
    private val encodedKeywords = MutableStateFlow<List<String>>(emptyList())

    override val keywordsFlow: Flow<List<String>> = encodedKeywords.map { list ->
        list.map(LocalObfuscator::decode)
    }

    override suspend fun get(): List<String> = encodedKeywords.value.map(LocalObfuscator::decode)

    override suspend fun saveKeywords(keywords: List<String>) {
        val normalized = keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        lock.withLock {
            encodedKeywords.value = normalized.map(LocalObfuscator::encode)
        }
    }
}
