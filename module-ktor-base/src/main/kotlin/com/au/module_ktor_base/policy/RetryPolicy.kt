package com.au.module_ktor_base.policy

import com.au.module_ktor_base.error.TransportFailure
import io.ktor.http.HttpMethod

class RetryPolicy(
    val maxResends: Int = 3,
    methods: Set<HttpMethod> = setOf(HttpMethod.Get, HttpMethod.Head),
    failures: Set<TransportFailure> = setOf(TransportFailure.Connect, TransportFailure.ConnectionLost),
    val backoffMillis: (attemptIndex: Int) -> Long = { attempt -> 250L * attempt },
) {
    private val methods = methods.toSet()
    private val failures = failures.toSet()
    init { require(maxResends >= 0) }

    fun allows(method: HttpMethod, failure: TransportFailure): Boolean = method in methods && failure in failures
    fun allowsReplay(method: HttpMethod): Boolean = method in methods
}
