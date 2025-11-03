package com.au.audiorecordplayer.cam2.view.gl

open class FilterExtraConfig()

data class FilterExtraSizeConfig(val width:Int, val height : Int) : FilterExtraConfig()

data class FilterExtraExposureConfig(val exposure: Float) : FilterExtraConfig()