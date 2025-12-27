package com.au.jobstudy.utils

interface IFactoryDayer {
    val yesterday: Int
    val currentDay:Int
    val weekStartDay:Int
    val lastWeekStartDay:Int
    fun isYesterdayIsLastWeek() : Boolean
}