package com.au.module_ktor_base.api

import com.au.module_ktor_base.client.KtorRequestExecutor
import com.au.module_ktor_base.error.PolicyConfigurationException
import com.au.module_ktor_base.policy.ApiResponsePolicy
import com.au.module_ktor_base.policy.TargetPolicy
import io.ktor.http.Url

/** 设备地址由 provider 解析，单次请求与重试始终绑定同一个设备 origin。 */
abstract class AbsKtorLanApi(
    executor: KtorRequestExecutor,
    baseUrlProvider: suspend () -> Url?,
    responsePolicy: ApiResponsePolicy? = null,
) : AbsKtorApi(executor, baseUrlProvider, responsePolicy) {
    init {
        if (executor.config.targetPolicy !is TargetPolicy.ResolvedBaseOrigin) {
            throw PolicyConfigurationException("LAN APIs require resolved base origin policy")
        }
    }
}
