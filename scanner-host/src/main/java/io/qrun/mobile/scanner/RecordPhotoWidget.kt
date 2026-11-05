/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kingsrook.qqq.frontend.android.mobileapp.ui.records.RecordAttachmentUi
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.WidgetBlockParameters
import java.io.File

/*******************************************************************************
 ** Native capture is opt-in metadata plus current server capabilities; receipts do not advance business steps.
 *******************************************************************************/
@Composable
internal fun RecordPhotoWidget(parameters: WidgetBlockParameters, api: RecordFilesApi, modifier: Modifier, disabled: Boolean) {
   val target = runCatching { RecordPhotoTarget.parse(parameters.widgetBlock.values) }.getOrNull()
   val host = parameters.qViewModel
   val process = host?.activeProcessViewModel
   if (target == null || host == null || process == null) {
      Text("This photo control has unsupported metadata or no active process.", modifier.padding(16.dp))
      return
   }
   RecordPhotoCapture(host, target, api, modifier, disabled, parameters.widgetBlock.blockId, process)
}

/*******************************************************************************
 ** Record details and process widgets share the same capability-gated upload implementation.
 *******************************************************************************/
@Composable
internal fun RecordPhotoCapture(
   host: com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel,
   target: RecordPhotoTarget, api: RecordFilesApi, modifier: Modifier = Modifier, disabled: Boolean = false,
   blockId: String = "record-photos",
   process: com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel? = null,
   recordUi: RecordAttachmentUi? = null,
) {
   val context = LocalContext.current
   val application = context.applicationContext
   val session = host.sessionUUID
   val processId = if (process == null) "record" else process.processUUID ?: return
   val processOwner = LocalViewModelStoreOwner.current as? NavBackStackEntry
   if (processOwner == null) {
      Text("Photo capture requires a record or process navigation scope.", modifier.padding(16.dp))
      return
   }
   val key = "photo:$session:$processId:$blockId:${target.tableName.length}:${target.tableName}:${target.recordId}"
   val factory = remember(key, api)
   {
      object : ViewModelProvider.Factory {
         /*******************************************************************************
          ** Construct upload state for the captured record/session rather than sharing it across unrelated widgets.
          *******************************************************************************/
         override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PhotoUploadViewModel(api, session, target, File(application.cacheDir, "record-photos")) {
               host.sessionUUID == session && host.isAuthenticated && !host.isLogoutRequested && host.activeProcessViewModel === process
                  && processOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)
            } as T
         }
      }
   }
   val upload: PhotoUploadViewModel = viewModel(viewModelStoreOwner = processOwner, key = key, factory = factory)
   val owner = LocalLifecycleOwner.current
   fun setWork(message: String?) {
      if (process != null) process.setClientWork(key, message) else host.qNavigator.recordWorkBlock = message
   }
   SideEffect {
      setWork(if (upload.pending || upload.busy) "Finish, retry/check, or discard the pending photo before leaving or continuing." else null)
   }
   DisposableEffect(owner, upload)
   {
      val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) upload.pause() }
      owner.lifecycle.addObserver(observer)
      onDispose {
         owner.lifecycle.removeObserver(observer)
         upload.pause()
         // A disposed configuration must not clear the retained upload's navigation guard.
         // Authentication teardown clears record work separately in the host.
         if (!upload.pending) setWork(null)
      }
   }
   val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> upload.cameraResult(success) }
   val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
         val media = runCatching { application.contentResolver.getType(uri) }.getOrNull().orEmpty()
         upload.galleryResult(media) { application.contentResolver.openInputStream(uri) }
      }
   }
   SideEffect { recordUi?.available?.invoke(upload.limits != null) }
   if (recordUi?.photosVisible == true) SavedRecordPhotos(host, target, api, upload.saved)
   val controls: @Composable () -> Unit = {
   Column((if (recordUi == null) modifier else modifier.verticalScroll(rememberScrollState())).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp))
   {
      if (recordUi == null) Text(target.title ?: "Record photos", style = MaterialTheme.typography.titleMedium)
      if (recordUi != null) Text(recordUi.recordLabel, style = MaterialTheme.typography.labelLarge)
      target.instructions?.let { Text(it) }
      when {
         upload.checking -> Text("Checking photo permissions…")
         upload.limits == null -> {
            Text("Photo upload is not available for this record.")
            TextButton(onClick = upload::checkCapabilities, enabled = !disabled && !upload.busy) { Text("Check photo availability") }
         }

         else -> {
            val available = !disabled && !upload.busy && !upload.pending
            if ("image/jpeg" in upload.limits!!.mediaTypes)
               Button(enabled = available, onClick = {
                  val file = upload.prepareCamera()
                  setWork("Return from the camera and finish or discard the photo before leaving.")
                  val uri = FileProvider.getUriForFile(context, context.packageName + ".photos", file)
                  try {
                     camera.launch(uri)
                  } catch (exception: Exception) {
                     upload.cameraResult(false)
                  }
               }) { Text("Take photo") }
            if (upload.limits!!.mediaTypes.any { it.startsWith("image/") })
               Button(enabled = available, onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose photo") }
            if (upload.intent != null && !upload.saved) {
               Button(enabled = !disabled && !upload.busy, onClick = upload::upload) { Text(if (upload.hasAttemptedUpload) "Retry/check upload" else "Upload photo") }
               TextButton(enabled = !disabled && !upload.busy, onClick = upload::discard) { Text("Discard photo") }
            }
         }
      }
      if (upload.busy) Text("Working on photo…")
      if (upload.message.isNotBlank()) Text(upload.message)
      if (upload.pending) Text("This app uploads while open and connected. Keep this screen open until the server confirms the photo.")
   }
   }
   if (recordUi == null) controls()
   else if (recordUi.captureVisible) AlertDialog(
      onDismissRequest = { if (!upload.pending && !upload.busy) recordUi.closeCapture() },
      title = { Text("Add photo") }, text = controls,
      confirmButton = { TextButton(enabled = !upload.pending && !upload.busy, onClick = recordUi.closeCapture) { Text("Done") } }
   )
}
