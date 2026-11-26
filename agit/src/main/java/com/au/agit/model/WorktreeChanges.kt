package com.au.agit.model

import java.nio.file.attribute.PosixFilePermission

enum class FileDiscardAction { DeleteUntracked, RestoreFromHead, RemoveStagedAddition }

data class FileDiscardPlan(
    val path: String,
    val action: FileDiscardAction,
    val head: String?,
    val indexEntry: String?,
    val fingerprint: String,
    val permissions: Set<PosixFilePermission>?,
)

data class CommitDiff(
    val kind: CommitDiffKind,
    val patch: String = "",
    val truncated: Boolean = false,
    val modeChange: Pair<String, String>? = null,
)

enum class CommitDiffKind { Text, Binary, TooLarge, Unchanged, MetadataOnly }
