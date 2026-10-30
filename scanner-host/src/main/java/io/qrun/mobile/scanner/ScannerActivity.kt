/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.kingsrook.qqq.frontend.android.core.controllers.NetworkQQQRepository
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.TableBasedAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.mobileapp.QMobileAppMain
import com.kingsrook.qqq.frontend.android.mobileapp.container.QAppContainer
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalAuthenticatedContent
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalCustomWidgetRenderer
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

/** Deployable generic host: select a server, authenticate, enroll, then run its QQQ mobile apps. */
class ScannerActivity : ComponentActivity()
{
   companion object
   {
      private val credentialsByServer = mutableMapOf<String, DeviceCredentials>()
      private val storesByServer = mutableMapOf<String, DataStore<Preferences>>()
   }

   override fun onCreate(savedInstanceState: Bundle?)
   {
      super.onCreate(savedInstanceState)
      val preferences = getSharedPreferences("host", MODE_PRIVATE)
      setContent()
      {
         MaterialTheme()
         {
            Surface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
            {
               var server by remember { mutableStateOf(preferences.getString("server", null)) }
               val currentServer = server
               if(currentServer == null)
               {
                  ServerForm { value -> preferences.edit().putString("server", value).apply(); server = value }
               }
               else
               {
                  key(currentServer)
                  {
                     HostChrome(currentServer, onChangeServer = { preferences.edit().remove("server").apply(); server = null })
                     { brand, diagnostics -> ScannerHost(currentServer, brand, diagnostics) }
                  }
               }
            }
         }
      }
   }

   /** Server-scoped stores ensure switching installations cannot reuse a credential accidentally. */
   @Composable
   private fun ScannerHost(server: String, brand: HostBrand, diagnostics: DiagnosticLog)
   {
      val credentialsResult = remember(server) { runCatching { credentialsByServer.getOrPut(server) { DeviceCredentials(applicationContext, server) } } }
      val credentials = credentialsResult.getOrNull()
      if(credentials == null)
      {
         Text("Device credentials could not be opened. Ask your administrator to reset this installation.", Modifier.padding(24.dp))
         return
      }
      val factory = remember(server)
      {
         QAppContainer.availableEnvironments = listOf(Environment("QQQ server", server))
         QViewModel.applicationName = "qqq-mobile-scanners"
         QViewModel.applicationVersion = BuildConfig.VERSION_NAME
         object : ViewModelProvider.Factory
         {
            override fun <T : ViewModel> create(modelClass: Class<T>): T
            {
               val hash = MessageDigest.getInstance("SHA-256").digest(server.toByteArray()).joinToString("") { "%02x".format(it) }
               val dataStore = storesByServer.getOrPut(server) { PreferenceDataStoreFactory.create { File(noBackupFilesDir, "session-$hash.preferences_pb") } }
               val repository = NetworkQQQRepository(server, requestObserver = diagnostics::nativeRequest) { requestedServer ->
                  if(requestedServer == server) credentials.headers() else emptyMap()
               }
               @Suppress("UNCHECKED_CAST")
               return QViewModel(repository, dataStore) as T
            }
         }
      }
      val qViewModel: QViewModel = viewModel(key = server, factory = factory)
      // A retained per-server ViewModel must write into this visit's fresh diagnostics log.
      SideEffect { (qViewModel.qqqRepository as NetworkQQQRepository).requestObserver = diagnostics::nativeRequest }
      qViewModel.setNavController(rememberNavController())
      val authentication = qViewModel.authenticationMetaDataLoadState
      if(authentication is LoadState.Success && authentication.value !is TableBasedAuthenticationMetaData)
      {
         Text("This build supports username/password sign-in. This server uses another sign-in provider. Configure that provider for this app or choose another server.", Modifier.padding(24.dp))
         return
      }
      val api = remember(server, diagnostics) { DeviceApi(server, credentials, diagnostics::deviceRequest) }
      CompositionLocalProvider(
         LocalAuthenticatedContent provides { model, content -> DeviceGate(model, api, credentials, content) },
         LocalCustomWidgetRenderer provides { parameters, modifier, disabled ->
            if(parameters.widgetBlock.blockType == "SCANNER")
            {
               ScannerWidget(parameters, modifier, disabled)
               true
            }
            else false
         }
      )
      {
         // HostChrome checks the actual server; an INTERNET network is not required for local USB forwarding.
         QMobileAppMain(qViewModel, title = brand.displayName, splashImage = null, showNetworkDialog = false)
      }
   }
}

/** URLs are origins only; release builds require HTTPS. */
internal fun normalizeServerUrl(input: String, allowHttp: Boolean): String
{
   val uri = URI(input.trim())
   require(uri.scheme == "https" || (allowHttp && uri.scheme == "http")) { "Use an HTTPS server URL." }
   require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Use a server URL without credentials, query, or fragment." }
   require(uri.path.isNullOrEmpty() || uri.path == "/") { "Use the server origin without a path." }
   return URI(uri.scheme.lowercase(), null, uri.host.lowercase(), uri.port, "/", null, null).toASCIIString()
}

/** Initial server configuration is editable until the host connects. */
@Composable
private fun ServerForm(onConnect: (String) -> Unit)
{
   var url by remember { mutableStateOf(if(BuildConfig.DEBUG) "http://127.0.0.1:8080/" else "https://") }
   var error by remember { mutableStateOf("") }
   Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp))
   {
      Text("Connect QQQ Mobile", style = MaterialTheme.typography.headlineMedium)
      OutlinedTextField(url, { url = it }, label = { Text("QQQ server URL") }, singleLine = true)
      Button(onClick = {
         try { onConnect(normalizeServerUrl(url, BuildConfig.DEBUG)) }
         catch(exception: Exception) { error = exception.message ?: "Invalid server URL" }
      }) { Text("Connect") }
      if(error.isNotBlank()) Text(error)
      if(BuildConfig.DEBUG) Text("Development build: HTTP is allowed for the local demo. The default URL uses USB port forwarding; see the TC22 setup guide.")
   }
}

/** Pending, revoked, or unavailable devices cannot expose application screens. */
@Composable
private fun DeviceGate(viewModel: QViewModel, api: DeviceApi, credentials: DeviceCredentials, content: @Composable () -> Unit)
{
   var attempt by remember { mutableIntStateOf(0) }
   var status by remember(viewModel.sessionUUID) { mutableStateOf("CHECKING") }
   var error by remember { mutableStateOf("") }
   var enrollmentConflict by remember { mutableStateOf(false) }
   LaunchedEffect(viewModel.sessionUUID, attempt)
   {
      status = "CHECKING"
      error = ""
      enrollmentConflict = false
      try
      {
         status = api.status(viewModel.sessionUUID, "${Build.MANUFACTURER} ${Build.MODEL}")
         if(status == "APPROVED") viewModel.loadQInstance()
      }
      catch(exception: CancellationException) { throw exception }
      catch(exception: EnrollmentConflict) { status = "ERROR"; error = exception.message ?: "Registration conflict"; enrollmentConflict = true }
      catch(exception: Exception) { status = "ERROR"; error = exception.message ?: "Device check failed." }
   }
   if(status == "APPROVED") content()
   else Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp))
   {
      Text("Device approval", style = MaterialTheme.typography.headlineMedium)
      Text(when(status) {
         "CHECKING" -> "Checking device registration…"
         "PENDING" -> "An administrator must approve this device before you can run mobile apps."
         "REVOKED" -> "This device has been revoked. Contact your administrator."
         else -> error.ifBlank { "Device status: $status" }
      })
      if(credentials.deviceId.isNotBlank()) Text("Device ID: ${credentials.deviceId}")
      Button(enabled = status != "CHECKING", onClick = { attempt++ }) { Text("Check approval") }
      if(enrollmentConflict) Button(onClick = { credentials.restartPendingEnrollment(); attempt++ }) { Text("Start new registration") }
      Button(onClick = { viewModel.requestLogout() }) { Text("Sign out") }
   }
}
