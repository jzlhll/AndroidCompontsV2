package com.allan.autoclickfloat.consts

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.au.module_android.Globals
import com.au.module_cached.AppDataStore
import com.au.module_android.simplelivedata.NoStickLiveData
import kotlinx.coroutines.launch

class OnePointAuto {
    /**
     * 是否打开功能的开关
     */
    val autoOnePointOpenLiveData = NoStickLiveData(false)

    private val _autoOnePointClickMsLiveData = MutableLiveData<Int>()
    /**
     * 自动点击的监听
     */
    val autoOnePointClickMsLiveData:LiveData<Int> = _autoOnePointClickMsLiveData

    private val _autoOnePointLiveData = MutableLiveData<Triple<Int, Int, Int?>>()
    /**
     * 自动点击的真实坐标点
     */
    val autoOnePointLiveData:LiveData<Triple<Int, Int, Int?>> = _autoOnePointLiveData

    private val _autoOnePointLocLiveData = MutableLiveData<Triple<Int, Int, Int?>>()
    /**
     * 自动点击的真实坐标点
     */
    val autoOnePointLocLiveData:LiveData<Triple<Int, Int, Int?>> = _autoOnePointLocLiveData

    /**
     * 自动点击被辅助服务自动点击了以后的回调
     */
    val autoOnePointBeClickedData = NoStickLiveData<Any>()

    fun loadAutoOnePointMs() {
        Globals.mainScope.launch {
            val v = AppDataStore.readInt("autoOnePointClickMs", 250)
            _autoOnePointClickMsLiveData.postValue(v)
        }
    }

    fun saveAutoOnePointMs(ms:Int) {
        Globals.mainScope.launch {
            AppDataStore.saveString("autoOnePointClickMs", ms.toString())
            _autoOnePointClickMsLiveData.postValue(ms)
        }
    }

    fun loadAutoOnePoint() {
        Globals.mainScope.launch {
            val x:Int = AppDataStore.readInt("auto_continuous_click_point_x", 100)
            val y:Int = AppDataStore.readInt("auto_continuous_click_point_y", 400)
            val rotation:Int = AppDataStore.readInt("auto_continuous_click_point_rot", -1)
            val fixRotation = if(rotation == -1) null else rotation

            val locx:Int = AppDataStore.readInt("auto_continuous_click_point_loc_x", 100)
            val locy:Int = AppDataStore.readInt("auto_continuous_click_point_loc_y", 400)
            val locrotation = AppDataStore.readInt("auto_continuous_click_point_loc_rot", -1)
            val fixLocRotation = if(locrotation == -1) null else locrotation

            _autoOnePointLiveData.postValue(Triple(x, y, fixRotation))
            _autoOnePointLocLiveData.postValue(Triple(locx, locy, fixLocRotation))
        }
    }

    fun saveAutoOnePoint(x:Int, y:Int, locationX:Int, locationY:Int, rotation:Int) {
        Log.d(Const.TAG, "saveAutoOnePoint rotation $rotation x-y $x $y")
        Globals.mainScope.launch {
            AppDataStore.saveInt(
                "auto_continuous_click_point_x", x)
            AppDataStore.saveInt(
                "auto_continuous_click_point_y", y)
            AppDataStore.saveInt(
                "auto_continuous_click_point_rot", rotation)

            AppDataStore.saveInt(
                "auto_continuous_click_point_loc_x", locationX)
            AppDataStore.saveInt(
                "auto_continuous_click_point_loc_y", locationY)
            AppDataStore.saveInt(
                "auto_continuous_click_point_loc_rot", rotation)

            _autoOnePointLiveData.postValue(Triple(x, y, rotation))
            _autoOnePointLocLiveData.postValue(Triple(locationX, locationY, rotation))
        }
    }
}