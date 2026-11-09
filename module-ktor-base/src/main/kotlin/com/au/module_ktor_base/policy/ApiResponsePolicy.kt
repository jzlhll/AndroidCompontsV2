package com.au.module_ktor_base.policy

import com.au.module_ktor_base.error.BusinessException
import com.au.module_ktor_base.error.ResponseDecodingException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 显式启用业务 envelope，数字和字符串 code 统一按字符串比较。 */
class ApiResponsePolicy(
    successCodes: Set<String>,
    val codeField: String = "code",
    val dataField: String = "data",
) {
    private val successCodes = successCodes.toSet()
    init { require(successCodes.isNotEmpty()) }

    fun data(element: JsonElement, requestId: String): JsonElement {
        val envelope = element as? JsonObject
            ?: throw ResponseDecodingException(requestId, IllegalArgumentException("Expected API envelope"))
        val code = (envelope[codeField] as? JsonPrimitive)?.content
            ?: throw ResponseDecodingException(requestId, IllegalArgumentException("Missing API code"))
        if (code !in successCodes) throw BusinessException(code, requestId)
        return envelope[dataField]
            ?: throw ResponseDecodingException(requestId, IllegalArgumentException("Missing API data"))
    }
}
