package com.imhere.core.model

@JvmInline
value class FindSessionId(val id: String) {
    companion object {
        val EMPTY = FindSessionId("")
    }
}
