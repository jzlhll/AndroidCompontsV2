package com.au.module_ktor_base.cache

import com.au.module_ktor_base.config.StorageNamespace
import io.ktor.client.plugins.cookies.CookiesStorage

interface NamespacedCookiesStorage : CookiesStorage {
    val namespace: StorageNamespace
    suspend fun clear()
}
