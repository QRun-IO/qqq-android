/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.os.Build
import android.os.Bundle
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiCompatibility
import com.kingsrook.qqq.frontend.android.core.controllers.NetworkQQQRepository
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.TableBasedAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.OAuth2AuthenticationMetaData
import com.kingsrook.qqq.frontend.android.mobileapp.QMobileAppMain
import com.kingsrook.qqq.frontend.android.mobileapp.container.QAppContainer
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalAuthenticatedContent
import com.kingsrook.qqq.frontend.android.mobileapp.ui.LocalCustomWidgetRenderer
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.LocalDeviceSettings
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.LocalBusinessNavigationEnabled
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Deployable generic host: select a server, authenticate, enroll, then run its QQQ mobile apps. */
class ScannerActivity : ComponentActivity()
{
   private var resumed by mutableStateOf(false)
   private var focused by mutableStateOf(false)
   private var captureBounds by mutableStateOf<Rect?>(null)
   private val windowFocusObserver = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused = it }

   override fun onResume()
   {
      super.onResume()
      resumed = true
      window.decorView.post { if(resumed) focused = window.decorView.hasWindowFocus() }
   }
   override fun onPause() { resumed = false; super.onPause() }
   override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); focused = hasFocus }

   override fun onDestroy()
   {
      val observer = window.decorView.viewTreeObserver
      if(observer.isAlive) observer.removeOnWindowFocusChangeListener(windowFocusObserver)
      super.onDestroy()
   }

   companion object
   {
      private val credentialsByServer = mutableMapOf<String, DeviceCredentials>()
      private val storesByServer = mutableMapOf<String, DataStore<Preferences>>()
   }

   override fun onCreate(savedInstanceState: Bundle?)
   {
      super.onCreate(savedInstanceState)
      window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(windowFocusObserver)
      OrientationPolicy.read(this).apply(this)
      val preferences = getSharedPreferences("host", MODE_PRIVATE)
      setContent()
      {
         MaterialTheme()
         {
            Surface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).onGloballyPositioned { coordinates ->
               val bounds = coordinates.boundsInWindow()
               captureBounds = Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
            })
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
                     val scope = rememberCoroutineScope()
                     var beforeChange by remember { mutableStateOf<suspend () -> Unit>({}) }
                     var changing by remember { mutableStateOf(false) }
                     HostChrome(currentServer, onChangeServer = {
                        if(!changing) scope.launch {
                           changing = true
                           beforeChange()
                           preferences.edit().remove("server").apply()
                           server = null
                        }
                     })
                     { brand, diagnostics, compatibility, changeServer -> ScannerHost(currentServer, brand, diagnostics, compatibility, changeServer) { beforeChange = it } }
                  }
               }
            }
         }
      }
   }

   /** Server-scoped stores ensure switching installations cannot reuse a credential accidentally. */
   @Composable
   private fun ScannerHost(server: String, brand: HostBrand, diagnostics: DiagnosticLog, compatibility: UiApiCompatibility, changeServer: () -> Unit,
      registerBeforeChange: (suspend () -> Unit) -> Unit)
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
               val repository = NetworkQQQRepository(server, requestObserver = diagnostics::nativeRequest, compatibility = compatibility) { requestedServer ->
                  if(requestedServer == server) credentials.headers() else emptyMap()
               }
               @Suppress("UNCHECKED_CAST")
               return QViewModel(repository, dataStore).apply { supportedCustomWidgetTypes = setOf("SCANNER", "RECORD_FILES_CAPTURE") } as T
            }
         }
      }
      val qViewModel: QViewModel = viewModel(key = server, factory = factory)
      // A retained per-server ViewModel must write into this visit's fresh diagnostics log.
      SideEffect {
         (qViewModel.qqqRepository as NetworkQQQRepository).requestObserver = diagnostics::nativeRequest
         qViewModel.changeServerAction = changeServer
      }
      qViewModel.setNavController(rememberNavController())
      val authentication = qViewModel.authenticationMetaDataLoadState
      if(authentication is LoadState.Success && authentication.value !is TableBasedAuthenticationMetaData && authentication.value !is OAuth2AuthenticationMetaData)
      {
         Text("This app supports QQQ password sign-in and server-advertised OAuth2 browser sign-in. This server uses an unsupported provider.", Modifier.padding(24.dp))
         return
      }
      val api = remember(server, diagnostics) { DeviceApi(server, credentials, diagnostics::deviceRequest, compatibility) }
      val recordFiles = remember(server, diagnostics) { RecordFilesApi(server, credentials::headers, compatibility, diagnostics::nativeRequest) }
      var approved by remember(qViewModel.sessionUUID) { mutableStateOf(false) }
      val location = rememberForegroundLocation(resumed && approved && qViewModel.isAuthenticated && !qViewModel.isLogoutRequested)
      val currentScreen = sessionScreen(qViewModel, resumed, focused, approved, captureBounds != null)
      var renderedScreen by remember(qViewModel.sessionUUID) { mutableStateOf<SessionScreen?>(null) }
      LaunchedEffect(currentScreen)
      {
         // Metadata may change before Compose replaces the previous screen's pixels.
         withFrameNanos { }
         withFrameNanos { }
         renderedScreen = currentScreen
      }
      val sessionApi = remember(server, diagnostics) { MobileSessionApi(server, credentials::headers, compatibility, diagnostics::sessionRequest) }
      val deviceSharing = remember(server, qViewModel.sessionUUID) { DeviceScreenSharing(applicationContext) }
      val sessions = remember(sessionApi, qViewModel.sessionUUID)
      {
         val monitoredSession = qViewModel.sessionUUID
         MobileSessionController(qViewModel.sessionUUID, sessionApi,
            readScreen = {
               val screen = sessionScreen(qViewModel, resumed, focused, approved, captureBounds != null)
               if(deviceSharing.active) screen.copy(ready = true, captureMode = CaptureMode.DEVICE_SCREEN, location = location.report)
               else screen.copy(ready = screen.ready && screen == renderedScreen, location = location.report)
            },
            capture = { lease -> if(lease.captureMode == CaptureMode.DEVICE_SCREEN) deviceSharing.frame(lease)
               else captureBounds?.let { captureAppScreen(window, it, lease) } },
            onUnauthorized = {
               runOnUiThread {
                  if(qViewModel.sessionUUID == monitoredSession) qViewModel.requestLogout()
               }
            }, onDeviceViewingStopped = deviceSharing::stop, onDeviceLease = deviceSharing::boundLease)
      }
      DisposableEffect(sessions)
      {
         qViewModel.beforeLogout = { deviceSharing.stop(); sessions.end("SIGN_OUT") }
         registerBeforeChange { sessions.end("SERVER_CHANGE"); qViewModel.logOut().join() }
         onDispose { deviceSharing.stop(); sessions.invalidate() }
      }
      SideEffect {
         deviceSharing.onSystemStop = sessions::pauseViewing
         deviceSharing.onStarted = sessions::allowViewing
      }
      LaunchedEffect(sessions, approved, qViewModel.isAuthenticated, qViewModel.isLogoutRequested)
      {
         if(approved && qViewModel.isAuthenticated && !qViewModel.isLogoutRequested) sessions.run()
      }
      CompositionLocalProvider(
         LocalBusinessNavigationEnabled provides approved,
         LocalDeviceSettings provides {
            OrientationSetting(this@ScannerActivity)
            DeviceScreenSharingControl(deviceSharing, sessions) {
               approved && qViewModel.isAuthenticated && !qViewModel.isLogoutRequested
            }
            if(api.shortName.isNotBlank()) Text("Device: ${api.shortName}")
            if(api.displayName.isNotBlank()) Text("Name: ${api.displayName}")
            Text("Device permissions", style = MaterialTheme.typography.titleSmall)
            LocationPermissionControl(location)
            OpenSourceLicensesButton()
            TextButton(onClick = {
               qViewModel.qNavigator.requestLeave("Change server and sign out?", "Change server", alwaysConfirm = true) { qViewModel.changeServerAction?.invoke() }
            }) { Text("Change server") }
         },
         LocalAuthenticatedContent provides { model, content -> DeviceGate(model, api, credentials, { approved = it }) {
            Column {
               ScreenViewingIndicator(sessions)
               content()
            }
         } },
         LocalCustomWidgetRenderer provides { parameters, modifier, disabled ->
            if(parameters.widgetBlock.blockType == "SCANNER")
            {
               ScannerWidget(parameters, modifier, disabled)
               true
            }
            else if(parameters.widgetBlock.blockType == "RECORD_FILES_CAPTURE")
            {
               RecordPhotoWidget(parameters, recordFiles, modifier, disabled)
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
   Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp))
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
private fun DeviceGate(viewModel: QViewModel, api: DeviceApi, credentials: DeviceCredentials, onApproval: (Boolean) -> Unit,
   content: @Composable () -> Unit)
{
   var attempt by remember { mutableIntStateOf(0) }
   var status by remember(viewModel.sessionUUID) { mutableStateOf("CHECKING") }
   var error by remember { mutableStateOf("") }
   var enrollmentConflict by remember { mutableStateOf(false) }
   LaunchedEffect(viewModel.sessionUUID, attempt)
   {
      status = "CHECKING"
      onApproval(false)
      error = ""
      enrollmentConflict = false
      try
      {
         status = api.status(viewModel.sessionUUID, "${Build.MANUFACTURER} ${Build.MODEL}")
         onApproval(status == "APPROVED")
         if(status == "APPROVED") viewModel.loadQInstance()
      }
      catch(exception: CancellationException) { throw exception }
      catch(exception: EnrollmentConflict) { status = "ERROR"; error = exception.message ?: "Registration conflict"; enrollmentConflict = true }
      catch(exception: Exception) { status = "ERROR"; error = exception.message ?: "Device check failed." }
   }
   if(status == "APPROVED") content()
   else Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp))
   {
      Text("Device approval", style = MaterialTheme.typography.headlineMedium)
      Text(when(status) {
         "CHECKING" -> "Checking device registration…"
         "PENDING" -> "An administrator must approve this device before you can run mobile apps."
         "REVOKED" -> "This device has been revoked. Contact your administrator."
         else -> error.ifBlank { "Device status: $status" }
      })
      if(credentials.deviceId.isNotBlank()) Text("Device ID: ${credentials.deviceId}")
      if(api.shortName.isNotBlank()) Text("Device: ${api.shortName}")
      if(api.displayName.isNotBlank()) Text("Name: ${api.displayName}")
      Text("Your administrator can edit this device’s name, short name and description in QQQ before approval.")
      Button(enabled = status != "CHECKING", onClick = { attempt++ }) { Text("Check approval") }
      if(enrollmentConflict) Button(onClick = { credentials.restartPendingEnrollment(); attempt++ }) { Text("Start new registration") }
      Button(onClick = { viewModel.qNavigator.requestSignOut() }) { Text("Sign out") }
   }
}
