package com.allan.androidlearning.activities2

import com.au.module_android.log.logd

class WorkImpl : IWork {
    override fun doWork() {
        logd { "do work in workImpl." }
    }
}