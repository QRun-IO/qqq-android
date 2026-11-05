/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.core.model.RecordData
import com.kingsrook.qqq.frontend.android.core.model.RecordSearch
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

/** Only retained file identity and public display metadata reach the gallery. */
internal data class SavedRecordPhoto(val id: String, val name: String, val mediaType: String, val size: Long, val sha256: String, val lifecycle: String) {
   companion object {
      fun parse(row: RecordData, target: RecordPhotoTarget): SavedRecordPhoto {
         require(row.id("tableName") == target.tableName && row.id("recordId") == target.recordId)
         return SavedRecordPhoto(requireNotNull(row.id("id")), row.display("fileName"), row.id("mediaType").orEmpty(),
            requireNotNull(row.id("sizeBytes")).toLong(), row.id("sha256").orEmpty(), row.id("lifecycle").orEmpty())
      }
   }
}

/** Parent-scoped metadata is visible even while the original awaits review. */
@Composable
internal fun SavedRecordPhotos(host: QViewModel, target: RecordPhotoTarget, api: RecordFilesApi, saved: Boolean) {
   var offset by rememberSaveable(target.tableName, target.recordId, host.sessionUUID) { mutableStateOf(0) }
   var refresh by remember { mutableStateOf(0) }
   var rows by remember { mutableStateOf<List<SavedRecordPhoto>>(emptyList()) }
   var hasNext by remember { mutableStateOf(false) }
   var loading by remember { mutableStateOf(true) }
   var failed by remember { mutableStateOf(false) }
   var preview by remember { mutableStateOf<SavedRecordPhoto?>(null) }
   val admission = remember { Semaphore(1) }
   LaunchedEffect(target, host.sessionUUID, offset, refresh, saved) {
      loading = true; failed = false
      try {
         val predicate = RecordSearch("image/", listOf("mediaType"), mapOf("tableName" to JsonPrimitive(target.tableName), "recordId" to JsonPrimitive(target.recordId)))
         // Record Files intentionally denies aggregate counts. One look-ahead row keeps paging bounded.
         val page = host.qqqRepository.searchRecords("record_files_file", "id", predicate, offset, 11).map { SavedRecordPhoto.parse(it, target) }
         hasNext = page.size > 10
         rows = page.take(10)
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (exception: Exception) { failed = true; rows = emptyList() }
      finally { loading = false }
   }
   Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("record.photos")) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
         Text(if (loading) "Loading photos…" else if (failed) "Photos unavailable" else "${offset + rows.size}${if (hasNext) "+" else ""} photos", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
         TextButton(onClick = { refresh++ }) { Text("Refresh photos") }
      }
      if (failed) Text("Photos could not be loaded. Check your connection and access, then refresh.")
      if (!failed && !loading && rows.isEmpty()) Text("No photos attached. Use Actions → Add photo.")
      LazyColumn(Modifier.weight(1f).testTag("record.photos.list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
         items(rows, key = { it.id }) { file ->
            Row(Modifier.testTag("record.photo:${file.id}"), verticalAlignment = Alignment.CenterVertically) {
               if (file.lifecycle == "AVAILABLE") PhotoImage(api, host.sessionUUID, file, admission, 256, Modifier.size(72.dp))
               else Text("Photo", Modifier.size(72.dp).padding(8.dp), style = MaterialTheme.typography.labelMedium)
               Column(Modifier.weight(1f).padding(start = 8.dp)) {
                  Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                  Text(if (file.lifecycle == "QUARANTINED") "Awaiting review" else if (file.lifecycle == "AVAILABLE") "Available" else "Unavailable", style = MaterialTheme.typography.labelMedium)
                  if (file.lifecycle == "AVAILABLE") TextButton(onClick = { preview = file }) { Text("Preview photo") }
               }
            }
         }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
         TextButton(enabled = !loading && offset > 0, onClick = { offset = (offset - 10).coerceAtLeast(0) }) { Text("Previous photos") }
         TextButton(enabled = !loading && !failed && hasNext, onClick = { offset += 10 }) { Text("Next photos") }
      }
   }
   preview?.let { file ->
      AlertDialog(onDismissRequest = { preview = null }, title = { Text("Photo preview") }, text = {
         Column { Text(file.name); PhotoImage(api, host.sessionUUID, file, admission, 1600, Modifier.fillMaxWidth()) }
      }, confirmButton = { TextButton(onClick = { preview = null }) { Text("Close preview") } })
   }
}

/** Decode a bounded bitmap in memory, without an image-loader disk cache or external URL. */
@Composable
private fun PhotoImage(api: RecordFilesApi, session: String, file: SavedRecordPhoto, admission: Semaphore, edge: Int, modifier: Modifier) {
   var bitmap by remember(session, file, edge) { mutableStateOf<Bitmap?>(null) }
   var failed by remember(session, file) { mutableStateOf(false) }
   var retry by remember { mutableStateOf(0) }
   LaunchedEffect(session, file, edge, retry) {
      failed = false
      try {
         bitmap = admission.withPermit {
            val bytes = api.image(session, file)
            withContext(Dispatchers.Default) {
               val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
               BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
               require(options.outWidth in 1..100000 && options.outHeight in 1..100000)
               options.inSampleSize = 1
               while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > edge) options.inSampleSize *= 2
               options.inJustDecodeBounds = false
               requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
            }
         }
      } catch (cancelled: CancellationException) { throw cancelled }
      catch (exception: Exception) { failed = true }
   }
   val image = bitmap
   if (image != null) Image(image.asImageBitmap(), "Attached photo", modifier.testTag("photo.image:$edge"), contentScale = ContentScale.Fit)
   else if (failed) TextButton(onClick = { retry++ }, modifier = modifier) { Text("Retry image") }
   else Text("Loading image…", modifier, style = MaterialTheme.typography.labelSmall)
}
