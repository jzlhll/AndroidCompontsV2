package com.allan.picnote.data

import androidx.annotation.StringRes

class PicNoteStorageException(
    @StringRes val messageRes: Int,
    cause: Throwable? = null,
    val messageArguments: List<Any> = emptyList(),
) : RuntimeException("PicNote storage error messageRes=$messageRes", cause)
