package com.au.appcompose.composeutils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit


//其他常用 包含了compose的Dp，Sp和像素之间的转换。需要有一个density。
//而density的来源。是 LocalDensity.current

//androidx.compose.ui.unit.Density
// Dp.toPx()           Convert Dp to pixels
// TextUnit.toPx()     Convert Sp to pixels.
// Int/Float.toDp()    Convert pixel value to Dp
// Int/Float.toSp()    Convert pixel value to Sp.

/**
 * 像素数值转成Compose的Sp TextUnit
 */
@Composable
fun Int.pxToSp(density: Density = LocalDensity.current) : TextUnit {
    return with(density) {
        this@pxToSp.toSp()
    }
}

/**
 * 像素数值转成Compose的Sp TextUnit
 */
@Composable
fun Float.pxToSp(density: Density = LocalDensity.current) : TextUnit {
    return with(density) {
        this@pxToSp.toSp()
    }
}


/**
 * 像素数值转成Compose的Sp TextUnit
 */
@Composable
fun Int.pxToDp(density: Density = LocalDensity.current) : Dp {
    return with(density) {
        this@pxToDp.toDp()
    }
}

/**
 * 像素数值转成Compose的Sp TextUnit
 */
@Composable
fun Float.pxToDp(density: Density = LocalDensity.current) : Dp {
    return with(density) {
        this@pxToDp.toDp()
    }
}

/**
 * 将Dp，转成成像素
 */
@Composable
fun Dp.ToPx(dp: Dp, density: Density = LocalDensity.current): Float {
    return with(density) { dp.toPx() }
}

//todo 刘海屏，两侧。
