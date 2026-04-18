package com.allan.mydroid.api

import com.au.module_android.Globals
import com.allan.mydroid.R

enum class MyDroidMode {
    None,
    Receiver,
    Send,
}

/**
 * 这是翻译给前端用的
 */
fun MyDroidMode.toName(): String {
    return when (this) {
        MyDroidMode.Receiver -> Globals.getString(R.string.my_droid_mode_receiver)
        MyDroidMode.Send -> Globals.getString(R.string.my_droid_mode_send)
        else -> Globals.getString(R.string.my_droid_mode_none)
    }
}