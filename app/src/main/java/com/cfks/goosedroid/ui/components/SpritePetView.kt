package com.cfks.goosedroid.ui.components
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cfks.goosedroid.model.SpriteCharacterState

@Composable
fun SpritePetView(
    characterState: SpriteCharacterState = SpriteCharacterState(),
    speechText: String? = null,
    onPet: () -> Unit = {},
    onPoke: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.wrapContentSize(),
        contentAlignment = Alignment.Center
    ) {
        // Layered Sprite Renderer
        Canvas(
            modifier = Modifier
                .size(100.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onPoke() },
                        onDoubleTap = { onPet() }
                    )
                }
        ) {
            val scaleX = if (characterState.facingRight) 1f else -1f
            val bounceY = if (characterState.animationFrame % 2 == 1) -5f else 0f

            val centerX = size.width / 2f
            val centerY = size.height / 2f

            withTransform({
                translate(centerX, centerY + bounceY)
                scale(scaleX, 1f, Offset.Zero)
            }) {
                // 1. Back Arm (Skin)
                drawRoundRect(
                    color = characterState.skinColor,
                    topLeft = Offset(-18f, 10f),
                    size = Size(10f, 25f),
                    cornerRadius = CornerRadius(5f)
                )

                // 2. Back Leg (Skin/Pants)
                val legOffset = if (characterState.animationFrame == 1) -8f else if (characterState.animationFrame == 2) 8f else 0f
                drawRoundRect(
                    color = characterState.skinColor,
                    topLeft = Offset(-12f + legOffset, 30f),
                    size = Size(10f, 20f),
                    cornerRadius = CornerRadius(5f)
                )

                // 3. Torso (Skin)
                drawRoundRect(
                    color = characterState.skinColor,
                    topLeft = Offset(-15f, 0f),
                    size = Size(30f, 35f),
                    cornerRadius = CornerRadius(10f)
                )

                // 4. Pants Layer
                drawRoundRect(
                    color = characterState.pantsColor,
                    topLeft = Offset(-16f, 18f),
                    size = Size(32f, 15f),
                    cornerRadius = CornerRadius(4f)
                )

                // 5. Shirt Layer
                drawRoundRect(
                    color = characterState.shirtColor,
                    topLeft = Offset(-16f, 0f),
                    size = Size(32f, 22f),
                    cornerRadius = CornerRadius(6f)
                )

                // 6. Front Leg
                drawRoundRect(
                    color = characterState.skinColor,
                    topLeft = Offset(2f - legOffset, 30f),
                    size = Size(10f, 20f),
                    cornerRadius = CornerRadius(5f)
                )

                // 7. Head (Skin)
                drawCircle(
                    color = characterState.skinColor,
                    radius = 22f,
                    center = Offset(0f, -22f)
                )

                // 8. Eyes Layer
                val eyeY = -25f
                val eyeSize = 3f
                drawCircle(Color.Black, radius = eyeSize, center = Offset(-6f, eyeY))
                drawCircle(Color.Black, radius = eyeSize, center = Offset(10f, eyeY))
                
                // Cute mouth
                drawArc(
                    color = Color.Black,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(-2f, -20f),
                    size = Size(6f, 6f),
                    style = Stroke(width = 2f)
                )

                // 9. Hair Layer
                drawArc(
                    color = characterState.hairColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(-24f, -44f),
                    size = Size(48f, 40f)
                )
                // Hair Bangs
                drawRoundRect(
                    color = characterState.hairColor,
                    topLeft = Offset(4f, -34f),
                    size = Size(12f, 15f),
                    cornerRadius = CornerRadius(5f)
                )

                // 10. Front Arm
                val armRot = if (characterState.animationFrame > 0) 15f else 0f
                withTransform({
                    translate(8f, 10f)
                    rotate(armRot * scaleX)
                }) {
                    drawRoundRect(
                        color = characterState.skinColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(10f, 25f),
                        cornerRadius = CornerRadius(5f)
                    )
                    // Shirt Sleeve
                    drawRoundRect(
                        color = characterState.shirtColor,
                        topLeft = Offset(-1f, -1f),
                        size = Size(12f, 12f),
                        cornerRadius = CornerRadius(4f)
                    )
                }
            }
        }

        // Speech Bubble
        if (!speechText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .offset { IntOffset(0, -110) }
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = speechText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
