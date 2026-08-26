package com.cfks.goosedroid.ui.alert

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Floating in-app alert banner rendered above all content.
 * Strictly monochrome: ERROR renders inverted (primary bg + onPrimary text),
 * everything else stays on surface grays.
 */
@Composable
fun AppAlertHost(modifier: Modifier = Modifier) {
    val alert by AlertBus.current.collectAsState()
    val visibleState = remember { MutableTransitionState(false) }
    var rendered by remember { mutableStateOf<AppAlert?>(null) }

    LaunchedEffect(alert?.id) {
        val a = alert ?: return@LaunchedEffect
        rendered = a
        visibleState.targetState = true
        delay(a.autoDismissMs)
        visibleState.targetState = false
        delay(280) // let exit animation play before clearing
        if (AlertBus.current.value?.id == a.id) AlertBus.dismiss()
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        rendered?.let { a ->
            AlertBanner(alert = a, onDismiss = { visibleState.targetState = false })
        }
    }
}

@Composable
private fun AlertBanner(alert: AppAlert, onDismiss: () -> Unit) {
    val isError = alert.type == AlertType.ERROR
    val tag = when (alert.type) {
        AlertType.SUCCESS -> "[OK]"
        AlertType.INFO -> "[INFO]"
        AlertType.WARNING -> "[WARN]"
        AlertType.ERROR -> "[ERR]"
    }

    Surface(
        color = if (isError) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isError) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        shape = RoundedCornerShape(6.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = tag,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (isError) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isError) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
                alert.message?.let { msg ->
                    Text(
                        text = msg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (isError) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "✕",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = if (isError) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(4.dp)
            )
        }
    }
}
