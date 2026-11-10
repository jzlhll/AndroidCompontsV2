package com.au.module_ktor_base.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ApiEnvelope<T>(val code: JsonPrimitive, val data: T, val message: String? = null)

@Serializable
data class Page<T>(val items: List<T>, val page: Int, val pageSize: Int, val total: Long? = null)

data class TransferProgress(val transferredBytes: Long, val totalBytes: Long?)
