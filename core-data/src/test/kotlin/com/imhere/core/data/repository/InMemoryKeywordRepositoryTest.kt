package com.imhere.core.data.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryKeywordRepositoryTest {
    @Test
    fun saveKeywords_normalizesAndDeduplicates() = runTest {
        val repo = InMemoryKeywordRepository()
        repo.saveKeywords(listOf(" 폰아 울려 ", "폰아 울려", "여기야"))

        assertEquals(listOf("폰아 울려", "여기야"), repo.get())
    }
}
