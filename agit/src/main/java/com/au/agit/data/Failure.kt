package com.au.agit.data

import com.au.agit.R
import com.au.agit.appString
import com.au.agit.model.AGitException
import java.io.IOException

data class Failure(val code: String, val message: String)

/** 仅输出受控错误分类，避免网络异常原文携带认证信息或远端响应。 */
fun safeFailure(error: Throwable): Failure {
    if (error is AGitException) return Failure(error.code, error.message ?: appString(R.string.operation_incomplete))
    if (error is LinkageError) return Failure("ENGINE_UNAVAILABLE", appString(R.string.engine_unavailable))
    val messages = generateSequence(error) { it.cause }.take(8).map { it.message.orEmpty().lowercase() }.joinToString(" ")
    return when {
        listOf("401", "not authorized", "authentication", "credentials", "auth fail").any { it in messages } ->
            Failure("AUTHENTICATION", appString(R.string.auth_failed))
        listOf("403", "forbidden", "protected", "permission denied").any { it in messages } ->
            Failure("PERMISSION", appString(R.string.permission_failed))
        "not found" in messages || "404" in messages -> Failure("NOT_FOUND", appString(R.string.repo_not_found))
        error is org.eclipse.jgit.api.errors.TransportException || error is IOException ->
            Failure("NETWORK_OR_STORAGE", appString(R.string.network_storage_failed))
        else -> Failure("UNEXPECTED", appString(R.string.unexpected_failed))
    }
}
