package com.thedailyflare.thumbnail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Local compatibility implementation for rememberCoroutineScope.
 * Keeps the export coroutine tied to the Compose screen lifecycle.
 */
@Composable
fun rememberCoroutineScope(): CoroutineScope {
    val scope = remember {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    DisposableEffect(Unit) {
        onDispose { scope.cancel() }
    }
    return scope
}
