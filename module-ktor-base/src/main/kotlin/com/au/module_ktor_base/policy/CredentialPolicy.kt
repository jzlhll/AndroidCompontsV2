package com.au.module_ktor_base.policy

import com.au.module_ktor_base.error.PolicyConfigurationException

enum class CredentialRequirement { Required, Optional, None }

sealed class CredentialPolicy {
    class FixedOrigins(requirements: Map<Origin, CredentialRequirement>) : CredentialPolicy() {
        val requirements: Map<Origin, CredentialRequirement> = requirements.toMap()
    }

    class ResolvedBaseOrigin(val requirement: CredentialRequirement) : CredentialPolicy()

    fun validate(target: TargetPolicy) {
        val valid = when {
            this is FixedOrigins && target is TargetPolicy.FixedOrigins -> requirements.keys == target.origins
            this is ResolvedBaseOrigin && target is TargetPolicy.ResolvedBaseOrigin -> true
            else -> false
        }
        if (!valid) throw PolicyConfigurationException("Credential declarations must exactly match target policy")
    }

    fun requirement(origin: Origin): CredentialRequirement = when (this) {
        is FixedOrigins -> requirements[origin] ?: throw PolicyConfigurationException("Missing origin credential declaration")
        is ResolvedBaseOrigin -> requirement
    }
}
