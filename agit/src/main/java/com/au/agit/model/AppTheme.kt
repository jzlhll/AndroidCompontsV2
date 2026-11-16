package com.au.agit.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppTheme { FollowSystem, Black, White, Dracula, Sage, Alucard }

fun AppTheme.resolve(systemDark: Boolean): AppTheme =
    if (this == AppTheme.FollowSystem) {
        if (systemDark) AppTheme.Black else AppTheme.White
    } else this

val AppTheme.dark: Boolean
    get() = this == AppTheme.Black || this == AppTheme.Dracula
