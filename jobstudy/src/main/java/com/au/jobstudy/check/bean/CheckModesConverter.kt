package com.au.jobstudy.check.bean

import androidx.room.TypeConverter
import com.au.jobstudy.check.modes.CheckMode
import com.au.module_gson.fromJsonList
import com.au.module_gson.gson

class CheckModesConverter {
    @TypeConverter
    fun listCheckModeToString(checkModes: List<CheckMode>) : String {
        return gson.toJson(checkModes)
    }

    @TypeConverter
    fun stringToListCheckMode(json:String) : List<CheckMode> {
        return json.fromJsonList<CheckMode>()
    }
}