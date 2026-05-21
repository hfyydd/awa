package com.example.awaassistant

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object Chat : NavKey
@Serializable data object Settings : NavKey
@Serializable data class NoteDetail(val recordId: Long) : NavKey
