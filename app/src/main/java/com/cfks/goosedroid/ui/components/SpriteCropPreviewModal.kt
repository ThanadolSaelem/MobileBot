package com.cfks.goosedroid.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cfks.goosedroid.ui.theme.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SpriteCropPreviewModal(
    originalBitmap: Bitmap,
    initialCols: Int,
    initialRows: Int,
    onDismiss: () -> Unit,
    onApplyCrop: (Bitmap, Int, Int) -> Unit
) {
    var cropLeftNorm by remember { mutableFloatStateOf(0f) }
    var cropTopNorm by remember { mutableFloatStateOf(0f) }
    var cropRightNorm by remember { mutableFloatStateOf(1f) }
    var cropBottomNorm by remember { mutableFloatStateOf(1f) }

    var cols by remember { mutableIntStateOf(initialCols.coerceIn(1, 32)) }
    var rows by remember { mutableIntStateOf(initialRows.coerceIn(1, 16)) }

    // Zoom & Pan states for precise editing
    var cropZoom by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Full screen dimmed & focused backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(horizontal = 12.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.96f)
                    .clip(RoundedCornerShape(12.dp)),
                color = TdsmSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, TdsmBorderLight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Modal Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "SPRITE CROP & GRID ALIGNMENT",
                                color = TdsmTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "SOURCE: ${originalBitmap.width}x${originalBitmap.height} PX",
                                color = TdsmTextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.clickable {
                                    cropLeftNorm = 0f
                                    cropTopNorm = 0f
                                    cropRightNorm = 1f
                                    cropBottomNorm = 1f
                                    cropZoom = 1.0f
                                    panOffset = Offset.Zero
                                },
                                shape = RoundedCornerShape(4.dp),
                                color = TdsmSurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, TdsmBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = TdsmTextSecondary, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("RESET", color = TdsmTextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Zoom & Precision Pan Controller Toolbar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF161616),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TdsmBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "CANVAS ZOOM",
                                color = TdsmTextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )

                            // Compact Zoom Controller: [-] [ 100% ] [+]
                            Surface(
                                color = TdsmSurfaceElevated,
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TdsmBorder)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            cropZoom = (cropZoom - 0.25f).coerceAtLeast(1.0f)
                                            if (cropZoom <= 1.0f) panOffset = Offset.Zero
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = TdsmTextPrimary, modifier = Modifier.size(12.dp))
                                    }

                                    Text(
                                        "${((cropZoom * 100).toInt())}%",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (cropZoom > 1.0f) Color(0xFF10B981) else TdsmTextPrimary,
                                        modifier = Modifier
                                            .clickable {
                                                cropZoom = 1.0f
                                                panOffset = Offset.Zero
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )

                                    IconButton(
                                        onClick = {
                                            cropZoom = (cropZoom + 0.25f).coerceAtMost(5.0f)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = TdsmTextPrimary, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Main Interactive Canvas Viewport (100% Focused on Cropping & Precision Pan)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF080808))
                            .border(1.dp, Color(0xFF262626), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val imageBitmap = remember(originalBitmap) { originalBitmap.asImageBitmap() }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    var activeHandle = ""

                                    detectDragGestures(
                                        onDragStart = { touchPos ->
                                            val canvasW = size.width.toFloat()
                                            val canvasH = size.height.toFloat()
                                            val origW = originalBitmap.width.toFloat()
                                            val origH = originalBitmap.height.toFloat()

                                            val baseScale = min(canvasW / origW, canvasH / origH) * 0.92f
                                            val curScale = baseScale * cropZoom
                                            val imgDrawW = origW * curScale
                                            val imgDrawH = origH * curScale
                                            val imgLeft = (canvasW - imgDrawW) / 2f + panOffset.x
                                            val imgTop = (canvasH - imgDrawH) / 2f + panOffset.y

                                            val curL = imgLeft + cropLeftNorm * imgDrawW
                                            val curT = imgTop + cropTopNorm * imgDrawH
                                            val curR = imgLeft + cropRightNorm * imgDrawW
                                            val curB = imgTop + cropBottomNorm * imgDrawH

                                            val touchRadius = 40f
                                            activeHandle = when {
                                                (touchPos - Offset(curL, curT)).getDistance() < touchRadius -> "TL"
                                                (touchPos - Offset(curR, curT)).getDistance() < touchRadius -> "TR"
                                                (touchPos - Offset(curL, curB)).getDistance() < touchRadius -> "BL"
                                                (touchPos - Offset(curR, curB)).getDistance() < touchRadius -> "BR"
                                                (touchPos - Offset((curL + curR) / 2f, curT)).getDistance() < touchRadius -> "T"
                                                (touchPos - Offset((curL + curR) / 2f, curB)).getDistance() < touchRadius -> "B"
                                                (touchPos - Offset(curL, (curT + curB) / 2f)).getDistance() < touchRadius -> "L"
                                                (touchPos - Offset(curR, (curT + curB) / 2f)).getDistance() < touchRadius -> "R"
                                                touchPos.x in curL..curR && touchPos.y in curT..curB && cropZoom <= 1.0f -> "CENTER"
                                                else -> "PAN"
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val canvasW = size.width.toFloat()
                                            val canvasH = size.height.toFloat()
                                            val origW = originalBitmap.width.toFloat()
                                            val origH = originalBitmap.height.toFloat()

                                            val baseScale = min(canvasW / origW, canvasH / origH) * 0.92f
                                            val curScale = baseScale * cropZoom
                                            val imgDrawW = origW * curScale
                                            val imgDrawH = origH * curScale

                                            if (activeHandle == "PAN") {
                                                // Pan the entire canvas image freely
                                                panOffset = Offset(
                                                    x = panOffset.x + dragAmount.x,
                                                    y = panOffset.y + dragAmount.y
                                                )
                                            } else {
                                                val dxNorm = dragAmount.x / imgDrawW
                                                val dyNorm = dragAmount.y / imgDrawH

                                                when (activeHandle) {
                                                    "TL" -> {
                                                        cropLeftNorm = (cropLeftNorm + dxNorm).coerceIn(0f, cropRightNorm - 0.02f)
                                                        cropTopNorm = (cropTopNorm + dyNorm).coerceIn(0f, cropBottomNorm - 0.02f)
                                                    }
                                                    "TR" -> {
                                                        cropRightNorm = (cropRightNorm + dxNorm).coerceIn(cropLeftNorm + 0.02f, 1f)
                                                        cropTopNorm = (cropTopNorm + dyNorm).coerceIn(0f, cropBottomNorm - 0.02f)
                                                    }
                                                    "BL" -> {
                                                        cropLeftNorm = (cropLeftNorm + dxNorm).coerceIn(0f, cropRightNorm - 0.02f)
                                                        cropBottomNorm = (cropBottomNorm + dyNorm).coerceIn(cropTopNorm + 0.02f, 1f)
                                                    }
                                                    "BR" -> {
                                                        cropRightNorm = (cropRightNorm + dxNorm).coerceIn(cropLeftNorm + 0.02f, 1f)
                                                        cropBottomNorm = (cropBottomNorm + dyNorm).coerceIn(cropTopNorm + 0.02f, 1f)
                                                    }
                                                    "T" -> {
                                                        cropTopNorm = (cropTopNorm + dyNorm).coerceIn(0f, cropBottomNorm - 0.02f)
                                                    }
                                                    "B" -> {
                                                        cropBottomNorm = (cropBottomNorm + dyNorm).coerceIn(cropTopNorm + 0.02f, 1f)
                                                    }
                                                    "L" -> {
                                                        cropLeftNorm = (cropLeftNorm + dxNorm).coerceIn(0f, cropRightNorm - 0.02f)
                                                    }
                                                    "R" -> {
                                                        cropRightNorm = (cropRightNorm + dxNorm).coerceIn(cropLeftNorm + 0.02f, 1f)
                                                    }
                                                    "CENTER" -> {
                                                        val curW = cropRightNorm - cropLeftNorm
                                                        val curH = cropBottomNorm - cropTopNorm
                                                        val newL = (cropLeftNorm + dxNorm).coerceIn(0f, 1f - curW)
                                                        val newT = (cropTopNorm + dyNorm).coerceIn(0f, 1f - curH)
                                                        cropLeftNorm = newL
                                                        cropTopNorm = newT
                                                        cropRightNorm = newL + curW
                                                        cropBottomNorm = newT + curH
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            val canvasW = size.width
                            val canvasH = size.height
                            val origW = originalBitmap.width.toFloat()
                            val origH = originalBitmap.height.toFloat()

                            val baseScale = min(canvasW / origW, canvasH / origH) * 0.92f
                            val curScale = baseScale * cropZoom
                            val imgDrawW = origW * curScale
                            val imgDrawH = origH * curScale
                            val imgLeft = (canvasW - imgDrawW) / 2f + panOffset.x
                            val imgTop = (canvasH - imgDrawH) / 2f + panOffset.y

                            // Draw original sprite image at pan & zoom
                            drawImage(
                                image = imageBitmap,
                                dstOffset = IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()),
                                dstSize = IntSize(imgDrawW.roundToInt(), imgDrawH.roundToInt())
                            )

                            // Dim outside crop bounds
                            val cropRect = Rect(
                                left = imgLeft + cropLeftNorm * imgDrawW,
                                top = imgTop + cropTopNorm * imgDrawH,
                                right = imgLeft + cropRightNorm * imgDrawW,
                                bottom = imgTop + cropBottomNorm * imgDrawH
                            )

                            // Dim masks
                            drawRect(Color.Black.copy(alpha = 0.65f), topLeft = Offset.Zero, size = Size(canvasW, cropRect.top.coerceAtLeast(0f)))
                            drawRect(Color.Black.copy(alpha = 0.65f), topLeft = Offset(0f, cropRect.bottom), size = Size(canvasW, (canvasH - cropRect.bottom).coerceAtLeast(0f)))
                            drawRect(Color.Black.copy(alpha = 0.65f), topLeft = Offset(0f, cropRect.top), size = Size(cropRect.left.coerceAtLeast(0f), cropRect.height))
                            drawRect(Color.Black.copy(alpha = 0.65f), topLeft = Offset(cropRect.right, cropRect.top), size = Size((canvasW - cropRect.right).coerceAtLeast(0f), cropRect.height))

                            // White crop border
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(cropRect.left, cropRect.top),
                                size = Size(cropRect.width, cropRect.height),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // Internal Grid Lines (Columns & Rows)
                            for (c in 1 until cols) {
                                val gx = cropRect.left + (cropRect.width / cols) * c
                                drawLine(
                                    color = Color.White.copy(alpha = 0.5f),
                                    start = Offset(gx, cropRect.top),
                                    end = Offset(gx, cropRect.bottom),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            for (r in 1 until rows) {
                                val gy = cropRect.top + (cropRect.height / rows) * r
                                drawLine(
                                    color = Color.White.copy(alpha = 0.5f),
                                    start = Offset(cropRect.left, gy),
                                    end = Offset(cropRect.right, gy),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            // Corner & Edge Drag Handles
                            val handleRadius = 6.dp.toPx()
                            drawCircle(Color.White, handleRadius, Offset(cropRect.left, cropRect.top))
                            drawCircle(Color.White, handleRadius, Offset(cropRect.right, cropRect.top))
                            drawCircle(Color.White, handleRadius, Offset(cropRect.left, cropRect.bottom))
                            drawCircle(Color.White, handleRadius, Offset(cropRect.right, cropRect.bottom))

                            // Edge midpoints
                            drawCircle(Color.White.copy(alpha = 0.7f), 4.dp.toPx(), Offset((cropRect.left + cropRect.right) / 2f, cropRect.top))
                            drawCircle(Color.White.copy(alpha = 0.7f), 4.dp.toPx(), Offset((cropRect.left + cropRect.right) / 2f, cropRect.bottom))
                            drawCircle(Color.White.copy(alpha = 0.7f), 4.dp.toPx(), Offset(cropRect.left, (cropRect.top + cropRect.bottom) / 2f))
                            drawCircle(Color.White.copy(alpha = 0.7f), 4.dp.toPx(), Offset(cropRect.right, (cropRect.top + cropRect.bottom) / 2f))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. Grid Steppers: Columns & Rows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Columns Stepper
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            color = TdsmSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TdsmBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("COLS: $cols", color = TdsmTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Row {
                                    IconButton(
                                        onClick = { cols = (cols - 1).coerceAtLeast(1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "-", tint = TdsmTextPrimary, modifier = Modifier.size(12.dp))
                                    }
                                    IconButton(
                                        onClick = { cols = (cols + 1).coerceAtMost(32) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "+", tint = TdsmTextPrimary, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }

                        // Rows Stepper
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            color = TdsmSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TdsmBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ROWS: $rows", color = TdsmTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Row {
                                    IconButton(
                                        onClick = { rows = (rows - 1).coerceAtLeast(1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "-", tint = TdsmTextPrimary, modifier = Modifier.size(12.dp))
                                    }
                                    IconButton(
                                        onClick = { rows = (rows + 1).coerceAtMost(16) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "+", tint = TdsmTextPrimary, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 5. Resolution details pill
                    val cropPixelW = ((cropRightNorm - cropLeftNorm) * originalBitmap.width).roundToInt()
                    val cropPixelH = ((cropBottomNorm - cropTopNorm) * originalBitmap.height).roundToInt()
                    val framePixelW = cropPixelW / max(1, cols)
                    val framePixelH = cropPixelH / max(1, rows)

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF141414),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TdsmBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("CROPPED: ${cropPixelW}x${cropPixelH} PX", fontSize = 8.sp, color = TdsmTextPrimary, fontFamily = FontFamily.Monospace)
                            Text("CELL: ${framePixelW}x${framePixelH} PX", fontSize = 8.sp, color = TdsmTextSecondary, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 6. Modal Bottom Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TdsmBorder)
                        ) {
                            Text("CANCEL", color = TdsmTextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = {
                                val finalCropped = generateCroppedBitmap(
                                    originalBitmap,
                                    cropLeftNorm,
                                    cropTopNorm,
                                    cropRightNorm,
                                    cropBottomNorm
                                )
                                if (finalCropped != null) {
                                    onApplyCrop(finalCropped, cols, rows)
                                }
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TdsmTextPrimary,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("APPLY CROP", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun generateCroppedBitmap(
    source: Bitmap,
    leftNorm: Float,
    topNorm: Float,
    rightNorm: Float,
    bottomNorm: Float
): Bitmap? {
    return try {
        val origW = source.width
        val origH = source.height

        val x = (leftNorm * origW).roundToInt().coerceIn(0, origW - 1)
        val y = (topNorm * origH).roundToInt().coerceIn(0, origH - 1)
        val w = ((rightNorm - leftNorm) * origW).roundToInt().coerceIn(1, origW - x)
        val h = ((bottomNorm - topNorm) * origH).roundToInt().coerceIn(1, origH - y)

        Bitmap.createBitmap(source, x, y, w, h)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
