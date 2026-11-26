package com.au.agit.model

sealed interface RepositoryPlan

data class BranchChoice(
    val branch: String,
    val target: String,
    val remoteHead: String,
    val localHead: String? = null,
    val merged: Boolean = false,
    val legacyDeliveryUnknown: Boolean = false,
    val configureUpstream: Boolean = false,
)

data class BranchChoicePlan(
    val projectId: String,
    val currentBranch: String,
    val currentHead: String?,
    val choices: List<BranchChoice>,
    val choosingInitialUpstream: Boolean = false,
) : RepositoryPlan {
    val initializing: Boolean get() = currentHead == null
}
