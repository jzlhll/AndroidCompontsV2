package com.au.audiorecordplayer.imgprocess

import android.view.View

sealed class LineInfo(val name:String,
                    val line: View,
                    val lineIndex:Int,
                    var isEnabled: Boolean)

class HorizontalLineInfo(name:String,
                         line: View,
                         lineIndex:Int,
                         var y: Float,
                         var minY: Float,
                         var maxY: Float,
                         isEnabled: Boolean) : LineInfo(name, line, lineIndex, isEnabled)

class VerticalLineInfo(name:String,
                       line: View,
                       lineIndex:Int,
                       var x: Float,
                       var minX: Float,
                       var maxX: Float,
                       isEnabled: Boolean) : LineInfo(name, line, lineIndex, isEnabled)