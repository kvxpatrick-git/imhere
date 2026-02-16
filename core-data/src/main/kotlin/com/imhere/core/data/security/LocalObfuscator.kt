package com.imhere.core.data.security

import java.util.Base64

object LocalObfuscator {
    fun encode(raw: String): String = Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
    fun decode(encoded: String): String = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
}
