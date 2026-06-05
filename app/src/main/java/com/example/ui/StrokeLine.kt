package com.example.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class StrokeLine(
    val points: List<Offset>,
    val color: Color = Color.Black,
    val strokeWidth: Float = 8f
)
