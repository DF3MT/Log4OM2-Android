package com.log4om.android.ui.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * User-visible text that ViewModels can emit without holding a Context.
 * Resolved in Compose (or via Context) against the current locale.
 */
sealed interface UiText {
    data class Resource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText {
        constructor(@StringRes resId: Int, vararg args: Any) : this(resId, args.toList())
    }

    /** Untranslated detail (e.g. JDBC/QRZ exception text). */
    data class Raw(val value: String) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Resource -> {
            if (args.isEmpty()) stringResource(resId)
            else stringResource(resId, *args.map { it.toString() }.toTypedArray())
        }
        is Raw -> value
    }

    fun asString(context: Context): String = when (this) {
        is Resource -> {
            if (args.isEmpty()) context.getString(resId)
            else context.getString(resId, *args.map { it.toString() }.toTypedArray())
        }
        is Raw -> value
    }
}
