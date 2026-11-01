/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A dedicated window keeps process input blocked while diagnostics are being inspected. */
@Composable
internal fun DiagnosticScreen(log: DiagnosticLog, onBack: () -> Unit)
{
   var events by remember(log) { mutableStateOf(log.snapshot()) }
   var paused by remember(log) { mutableStateOf(false) }
   var problemsOnly by remember(log) { mutableStateOf(false) }
   val clipboard = LocalClipboardManager.current
   val context = LocalContext.current
   val snackbar = remember { SnackbarHostState() }
   val scope = rememberCoroutineScope()
   LaunchedEffect(log, paused)
   {
      while(!paused)
      {
         events = log.snapshot()
         delay(1000)
      }
   }
   val rows = diagnosticRows(events, problemsOnly)
   Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false))
   {
      Surface(Modifier.fillMaxSize())
      {
         Column(Modifier.fillMaxSize().systemBarsPadding())
         {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            {
               TextButton(onClick = onBack) { Text("Back") }
               Text("Diagnostics", Modifier.padding(12.dp).weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp))
            {
               item()
               {
                  ConnectionSummary(events, paused)
               }
               item()
               {
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp))
                  {
                     Text("Recent activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                     Text("${rows.size} shown · Latest 100 events · Newest first", style = MaterialTheme.typography.bodySmall)
                     Row(horizontalArrangement = Arrangement.spacedBy(8.dp))
                     {
                        FilterChip(selected = !problemsOnly, onClick = { problemsOnly = false }, label = { Text("All") })
                        FilterChip(selected = problemsOnly, onClick = { problemsOnly = true }, label = { Text("Problems") })
                     }
                     OutlinedButton(onClick = {
                        if(!paused) events = log.snapshot()
                        paused = !paused
                     }) { Text(if(paused) "Resume live updates" else "Pause updates") }
                  }
               }
               if(rows.isEmpty()) item()
               {
                  Text(if(problemsOnly && events.isNotEmpty()) "No problems in the recorded activity." else "No activity recorded yet. New events appear here while this screen is live.",
                     style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 24.dp))
               }
               items(rows) { event -> DiagnosticEventRow(event) }
               item()
               {
                  Text("Safe to share: no credentials, scan values, server addresses or response bodies are recorded.", style = MaterialTheme.typography.bodySmall)
               }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly)
            {
               TextButton(modifier = Modifier.weight(1f), enabled = rows.isNotEmpty(), onClick = {
                  clipboard.setText(AnnotatedString(diagnosticReport(events, problemsOnly)))
                  scope.launch { snackbar.showSnackbar("Diagnostics copied") }
               }) { Text("Copy") }
               TextButton(modifier = Modifier.weight(1f), enabled = rows.isNotEmpty(), onClick = {
                  val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                     .putExtra(Intent.EXTRA_TEXT, diagnosticReport(events, problemsOnly))
                  try { context.startActivity(Intent.createChooser(intent, "Share diagnostics")) }
                  catch(exception: ActivityNotFoundException) { scope.launch { snackbar.showSnackbar("No app is available to share diagnostics") } }
               }) { Text("Share") }
               TextButton(modifier = Modifier.weight(1f), enabled = events.isNotEmpty(), onClick = {
                  log.clear()
                  events = emptyList()
                  scope.launch { snackbar.showSnackbar("Activity cleared") }
               }) { Text("Clear") }
            }
            SnackbarHost(snackbar)
         }
      }
   }
}

@Composable
private fun ConnectionSummary(events: List<DiagnosticEvent>, paused: Boolean)
{
   val check = latestConnectionCheck(events)
   val reachable = check?.code == DiagnosticCode.CONNECTION_OK
   Card(colors = CardDefaults.cardColors(containerColor = if(check != null && !reachable) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant))
   {
      Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp))
      {
         Text("Server connection", style = MaterialTheme.typography.labelLarge)
         Text(when { check == null -> "No check recorded"; reachable -> "Reachable"; else -> "Check failed" },
            style = MaterialTheme.typography.headlineSmall)
         if(check != null)
         {
            Text("Last check ${check.localTime()} · ${check.elapsedMillis} ms", style = MaterialTheme.typography.bodyMedium)
         }
         Text(if(paused) "Updates paused · Showing a saved snapshot" else "Live · Activity updates automatically", style = MaterialTheme.typography.bodySmall)
         Text("Reachability only. Sign-in and device approval are checked separately.", style = MaterialTheme.typography.bodySmall)
      }
   }
}

@Composable
private fun DiagnosticEventRow(event: DiagnosticEvent)
{
   Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp))
   {
      Text(event.localTime(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(event.title(), style = MaterialTheme.typography.titleSmall,
         color = if(event.isProblem()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
      Text(event.detail(), style = MaterialTheme.typography.bodyMedium)
      HorizontalDivider(Modifier.padding(top = 8.dp))
   }
}
