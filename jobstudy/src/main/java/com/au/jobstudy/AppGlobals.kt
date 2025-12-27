package com.au.jobstudy

import com.au.jobstudy.star.StarConsts
import com.au.jobstudy.utils.ISingleDayer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object AppGlobals : KoinComponent {
    val starConsts : StarConsts by inject()
    val dayer : ISingleDayer by inject()
}