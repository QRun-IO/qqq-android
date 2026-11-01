/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.WidgetBlockParameters
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel

/** Applications can enforce device enrollment before exposing native QQQ content. */
val LocalAuthenticatedContent = staticCompositionLocalOf<@Composable (QViewModel, @Composable () -> Unit) -> Unit>()
{
   { _, content -> content() }
}

/** Unknown widgets can be implemented by a host without forking the standard renderer. */
val LocalCustomWidgetRenderer = staticCompositionLocalOf<@Composable (WidgetBlockParameters, Modifier, Boolean) -> Boolean>()
{
   { _, _, _ -> false }
}

/** Live surface availability for asynchronous input; nested hosts must preserve the parent's gate. */
val LocalProcessInputAllowed = compositionLocalOf<() -> Boolean> { { true } }

/** Host surface priority shared by toolbar and system Back, independent of callback registration order. */
internal val LocalNavigationBackAction = compositionLocalOf<(() -> Unit)?> { null }
