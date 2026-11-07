package com.au.agit.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppTheme(val dark: Boolean) { Black(true), White(false), Dracula(true), Sage(false) }
