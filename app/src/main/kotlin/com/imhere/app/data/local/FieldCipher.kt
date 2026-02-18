package com.imhere.app.data.local

import android.util.Base64

object FieldCipher {
    fun protect(raw: String): String {
        return Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun reveal(protectedValue: String): String {
        return String(Base64.decode(protectedValue, Base64.DEFAULT), Charsets.UTF_8)
    }
}
