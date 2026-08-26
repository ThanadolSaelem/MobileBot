package com.cfks.goosedroid.ui.alert

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AlertType { SUCCESS, INFO, WARNING, ERROR }

data class AppAlert(
    val id: Long = System.currentTimeMillis(),
    val type: AlertType,
    val title: String,
    val message: String? = null,
    val autoDismissMs: Long = 4000L
)

/**
 * App-wide in-app alert bus. Any layer can post user-facing events
 * (connection results, downloads, import/export, errors) without a
 * reference to an Activity. Rendered by [AppAlertHost] — strictly
 * monochrome styling.
 */
object AlertBus {

    private val _current = MutableStateFlow<AppAlert?>(null)
    val current: StateFlow<AppAlert?> = _current.asStateFlow()

    fun show(
        type: AlertType,
        title: String,
        message: String? = null,
        autoDismissMs: Long = 4000L
    ) {
        _current.value = AppAlert(
            type = type,
            title = title,
            message = message,
            autoDismissMs = autoDismissMs
        )
    }

    fun dismiss() {
        _current.value = null
    }
}
