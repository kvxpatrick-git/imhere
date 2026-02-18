package com.imhere.core.domain.port

import kotlinx.coroutines.flow.Flow

interface KeywordRepository {
    suspend fun get(): List<String>
    suspend fun saveKeywords(keywords: List<String>)
    val keywordsFlow: Flow<List<String>>
}
