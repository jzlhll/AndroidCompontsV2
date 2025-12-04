package com.au.module_android.simpleflow

import androidx.lifecycle.ViewModel

abstract class AbsActionDispatcherViewModel : ViewModel(), IActionDispatcher by ActionDispatcherImpl() {
}