/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.ActivityNotFoundException
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/*******************************************************************************
 ** Keep product information and public website links in secondary settings;
 ** Query's bundled artwork remains visible without a server connection.
 *******************************************************************************/
@Composable
fun AboutButton() {
   var visible by rememberSaveable { mutableStateOf(false) }
   TextButton(onClick = { visible = true }) { Text("About QQQ Mobile") }
   if (visible) {
      Dialog(onDismissRequest = { visible = false }, properties = DialogProperties(usePlatformDefaultWidth = false))
      {
         Surface(Modifier.fillMaxSize())
         {
            Column(Modifier.fillMaxSize().safeDrawingPadding())
            {
               Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically)
               {
                  Text("About", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                  TextButton(onClick = { visible = false }) { Text("Back") }
               }
               Column(
                  Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                  verticalArrangement = Arrangement.spacedBy(16.dp)
               )
               {
                  Image(painterResource(R.drawable.query_mascot), "Query, the QRun mascot", Modifier.size(144.dp))
                  Text("QQQ Mobile", style = MaterialTheme.typography.headlineSmall)
                  Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                  Text("Meet Query", style = MaterialTheme.typography.titleMedium)
                  Text("Query is the QRun mascot. Learn about Query and how we use QQQ ourselves.")
                  AboutWebsiteLink("Dogfooding at QRun", "https://www.qrun.io/dogfooding")
                  AboutWebsiteLink("Meet Query", "https://www.qrun.io/query")
                  OpenSourceLicensesButton()
               }
            }
         }
      }
   }
}


/*******************************************************************************
 ** Open only fixed public information URLs in the external browser, without
 ** adding server addresses, session information or device identifiers.
 *******************************************************************************/
@Composable
private fun AboutWebsiteLink(label: String, url: String) {
   val handler = LocalUriHandler.current
   var unavailable by rememberSaveable { mutableStateOf(false) }
   TextButton(onClick = {
      unavailable = false
      try {
         handler.openUri(url)
      } catch (exception: ActivityNotFoundException) {
         unavailable = true
      } catch (exception: IllegalArgumentException) {
         unavailable = true
      }
   }) { Text(label) }
   Text(url, style = MaterialTheme.typography.bodySmall)
   if (unavailable) Text("No browser is available to open this link.", color = MaterialTheme.colorScheme.error)
}
