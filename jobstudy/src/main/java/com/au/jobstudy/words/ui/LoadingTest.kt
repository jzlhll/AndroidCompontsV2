package com.au.jobstudy.words.ui

import com.au.module_android.log.logdNoFile
import java.util.UUID

class LoadingTest {
    private val tag = "LoadingTest-" + UUID.randomUUID().toString()

    private var count = 0

    fun add() {
        count++
        logdNoFile { tag + "add: " + count }
    }
}