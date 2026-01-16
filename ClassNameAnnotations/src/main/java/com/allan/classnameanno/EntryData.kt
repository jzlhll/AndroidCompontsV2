package com.allan.classnameanno

data class EntryData(
    var customName: String,
    var priority: Int,
    var textColor: String,
    var backgroundColor: String,

    var autoEnter: Boolean
) {
    fun toCreatorString() : String {
        return "EntryData(\"$customName\", $priority, \"$textColor\", \"$backgroundColor\", $autoEnter)"
    }
}