package com.cfks.goosedroid.model

import androidx.compose.ui.graphics.Color

data class SpriteCharacterState(
    val skinColor: Color = Color(0xFFFFDFC4),
    val hairStyle: Int = 0,
    val hairColor: Color = Color(0xFF4A3000),
    val shirtStyle: Int = 0,
    val shirtColor: Color = Color(0xFF3B82F6),
    val pantsColor: Color = Color(0xFF1E3A8A),
    val eyeStyle: Int = 0,
    val animationFrame: Int = 0, // 0 for idle, 1 for walk1, 2 for walk2
    val facingRight: Boolean = true
)
