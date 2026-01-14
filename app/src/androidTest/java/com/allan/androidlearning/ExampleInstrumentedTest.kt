package com.allan.androidlearning

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.au.module_android.Globals
import com.au.module_gson.fromGson
import com.au.module_gson.fromGsonList
import com.au.module_gson.toGsonString
import com.au.module_android.utils.asOrNull
import com.au.module_android.log.logd

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.allan.androidlearning", appContext.packageName)

    }

    @Test
    fun testJson() {
        val bean1 = Bean("first", 1)
        val bean2 = Bean("second", 2)

        val bean1Str = bean1.toGsonString()
        val bean2Str = bean2.toGsonString()

        val revert1 = bean1Str.fromGson<Bean>()
        val revert2 = bean2Str.fromGson<Bean>()

        val list = listOf(bean1, bean2)
        val listStr = list.toGsonString()
        val revertList = listStr.fromGsonList<Bean>()
        val revertList2 = listStr.fromGsonList(Bean::class.java)

        logd { "beans1 $bean1Str bean2str $bean2Str $listStr $revertList" }
    }

    @Test
    fun testColorful() {
        Globals.topActivity.asOrNull<EntryActivity>()?.apply {

        }
    }
}