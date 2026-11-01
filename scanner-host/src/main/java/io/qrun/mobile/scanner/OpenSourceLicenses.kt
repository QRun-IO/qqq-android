/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults

/*******************************************************************************
 ** Secondary settings entry; library definitions and complete license texts are bundled resources.
 *******************************************************************************/
@Composable
fun OpenSourceLicensesButton() {
   var visible by rememberSaveable { mutableStateOf(false) }
   TextButton(onClick = { visible = true }) { Text("Open-source licenses") }
   if (visible) {
      Dialog(onDismissRequest = { visible = false }, properties = DialogProperties(usePlatformDefaultWidth = false))
      {
         Surface(modifier = Modifier.fillMaxSize())
         {
            Column(Modifier.fillMaxSize().safeDrawingPadding())
            {
               Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
               {
                  Text("Open-source licenses", modifier = Modifier.weight(1f).padding(vertical = 16.dp), style = MaterialTheme.typography.titleLarge)
                  TextButton(onClick = { visible = false }) { Text("Close") }
               }
               // AboutLibraries owns parsing and license rendering; no network or authentication is involved.
               LibrariesContainer(
                  modifier = Modifier.fillMaxWidth().weight(1f),
                  colors = LibraryDefaults.libraryColors(
                     backgroundColor = MaterialTheme.colorScheme.surface,
                     contentColor = MaterialTheme.colorScheme.onSurface,
                     badgeBackgroundColor = MaterialTheme.colorScheme.primary,
                     badgeContentColor = MaterialTheme.colorScheme.onPrimary,
                     dialogConfirmButtonColor = MaterialTheme.colorScheme.primary
                  )
               )
            }
         }
      }
   }
}
