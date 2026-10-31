/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import com.kingsrook.qqq.frontend.android.core.controllers.UiApiCompatibility
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiCompatibilityException
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.LocalOpenDiagnostics
import kotlinx.coroutines.CancellationException
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.svg.SvgDecoder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/** Lifetime is keyed by selected server: presentation and diagnostics cannot bleed across customers. */
@Composable
internal fun HostChrome(server: String, onChangeServer: () -> Unit, content: @Composable (HostBrand, DiagnosticLog, UiApiCompatibility, () -> Unit) -> Unit)
{
   val runtime = remember(server) { HostRuntime(server) }
   var compatible by remember(server) { mutableStateOf(false) }
   var compatibilityError by remember(server) { mutableStateOf<String?>(null) }
   var attempt by remember(server) { mutableIntStateOf(0) }
   var brand by remember(server) { mutableStateOf(HostBrand()) }
   var logo by remember(server) { mutableStateOf<ByteArray?>(null) }
   var connection by remember(server) { mutableStateOf("Checking") }
   var checkedAt by remember(server) { mutableStateOf("not checked") }
   var showDiagnostics by remember(server) { mutableStateOf(false) }
   var diagnosticText by remember(server) { mutableStateOf("") }
   val context = LocalContext.current
   val lifecycle = LocalLifecycleOwner.current.lifecycle
   val imageLoader = remember(context) { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
   DisposableEffect(imageLoader) { onDispose { imageLoader.shutdown() } }
   LaunchedEffect(runtime, attempt)
   {
      compatibilityError = null
      try { runtime.verifyCompatibility() }
      catch(exception: CancellationException) { throw exception }
      catch(exception: Exception)
      {
         compatibilityError = if(exception is UiApiCompatibilityException) exception.message else "Unable to check QQQ UI API compatibility. Check the server address and connection, then retry."
         connection = if(exception is UiApiCompatibilityException) "Incompatible server" else "Disconnected"
         runtime.diagnostics.record(DiagnosticCode.COMPATIBILITY_FAILED, null, 0)
         return@LaunchedEffect
      }
      brand = runtime.branding()
      compatible = true
      logo = brand.logoUrl?.let { runtime.logo(it) }
      diagnosticText = runtime.diagnostics.text()
   }
   LaunchedEffect(runtime, lifecycle, compatible)
   {
      if(!compatible) return@LaunchedEffect
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)
      {
         val changed = Channel<Unit>(Channel.CONFLATED)
         val connectivity = context.getSystemService(ConnectivityManager::class.java)
         val callback = object : ConnectivityManager.NetworkCallback()
         {
            override fun onAvailable(network: Network) { changed.trySend(Unit) }
            override fun onLost(network: Network) { changed.trySend(Unit) }
         }
         connectivity.registerDefaultNetworkCallback(callback)
         try
         {
            connection = "Checking"
            while(isActive)
            {
               connection = if(runtime.connected()) "Connected" else "Disconnected"
               checkedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
               diagnosticText = runtime.diagnostics.text()
               withTimeoutOrNull(5000) { changed.receive() }
            }
         }
         finally { connectivity.unregisterNetworkCallback(callback); changed.close() }
      }
   }
   val primary = Color(0xFF000000 or brand.primaryColor.drop(1).toLong(16))
   val onPrimary = if(useBlackOnPrimary(brand.primaryColor)) Color.Black else Color.White
   MaterialTheme(colorScheme = lightColorScheme(primary = primary, onPrimary = onPrimary,
      primaryContainer = lerp(primary, Color.White, 0.85f), onPrimaryContainer = Color.Black))
   {
      Surface()
      {
         Column(Modifier.fillMaxSize())
         {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically)
            {
               logo?.let { AsyncImage(model = it, imageLoader = imageLoader, contentDescription = "${brand.displayName} logo", modifier = Modifier.size(40.dp)) }
               Text(brand.displayName, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
               if(!compatible) TextButton(onClick = onChangeServer) { Text("Change server") }
            }
            Box(Modifier.weight(1f))
            {
               if(compatible) CompositionLocalProvider(LocalOpenDiagnostics provides { diagnosticText = runtime.diagnostics.text(); showDiagnostics = true })
               { content(brand, runtime.diagnostics, runtime.compatibility, onChangeServer) }
               else Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp))
               {
                  Text(compatibilityError ?: "Checking QQQ UI API compatibility…")
                  if(compatibilityError != null) Button(onClick = { attempt++ }) { Text("Retry compatibility check") }
               }
            }
            HorizontalDivider()
            TextButton(onClick = { diagnosticText = runtime.diagnostics.text(); showDiagnostics = true }, modifier = Modifier.fillMaxWidth())
            {
               Text("$connection · $checkedAt · Diagnostics", color = MaterialTheme.colorScheme.onSurface)
            }
         }
      }
      if(showDiagnostics)
      {
         val clipboard = LocalClipboardManager.current
         AlertDialog(onDismissRequest = { showDiagnostics = false }, title = { Text("Connection diagnostics") },
            text = {
               Column()
               {
                  Text("Connection checks server reachability only. Sign-in and device approval are checked separately.")
                  Text(diagnosticText.ifBlank { "No events." }, Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
               }
            },
            confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(diagnosticText)) }) { Text("Copy") } },
            dismissButton = {
               Row()
               {
                  TextButton(onClick = { runtime.diagnostics.clear(); diagnosticText = "" }) { Text("Clear") }
                  TextButton(onClick = { showDiagnostics = false }) { Text("Close") }
               }
            })
      }
   }
}
