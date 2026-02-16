package com.imhere.core.domain.port

import kotlinx.coroutines.flow.Flow

interface KeywordRepository {
    val keywordsFlow: Flow<List<String>>
}
