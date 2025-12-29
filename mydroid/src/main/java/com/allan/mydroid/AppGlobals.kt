package com.allan.mydroid

import com.allan.mydroid.globals.GlobalDroidServer
import com.allan.mydroid.globals.IDroidServerAliveTrigger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object AppGlobals : KoinComponent {
    val droidServerLiveTrigger : IDroidServerAliveTrigger by inject()
    val globalDroidServer : GlobalDroidServer by inject()
}