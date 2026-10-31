/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.InputStream
import java.io.Closeable
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One photo intent belongs to one authenticated process. Rotation retains it; logout never reassigns its actor. */
internal class PhotoUploadViewModel(private val api: RecordFilesTransport, private val session: String,
   private val target: RecordPhotoTarget, private val directory: File, private val io: CoroutineDispatcher = Dispatchers.IO,
   private val isCurrentSession: () -> Boolean) : ViewModel()
{
   var limits by mutableStateOf<PhotoUploadLimits?>(null)
      private set
   var checking by mutableStateOf(true)
      private set
   var busy by mutableStateOf(false)
      private set
   var message by mutableStateOf("")
      private set
   var intent by mutableStateOf<PhotoUploadIntent?>(null)
      private set
   var captureFile by mutableStateOf<File?>(null)
      private set
   var uploadId by mutableStateOf<String?>(null)
      private set
   var saved by mutableStateOf(false)
      private set
   private var createAttempted = false
   val hasAttemptedUpload: Boolean get() = createAttempted
   private var operation: Job? = null
   private var released = false
   private val ownedFiles = ConcurrentHashMap.newKeySet<File>()
   private val copyingInput = AtomicReference<Closeable?>()
   val pending: Boolean get() = captureFile != null || intent != null && !saved

   init { checkCapabilities() }

   /** Capability denial hides photo actions without disabling the rest of the process. */
   fun checkCapabilities()
   {
      if(busy || released) return
      operation = viewModelScope.launch {
         checking = true
         try { checkSession(); limits = api.capabilities(session, target) }
         catch(exception: CancellationException) { throw exception }
         catch(exception: Exception) { limits = null; message = "Photo upload is unavailable. Check the connection or ask your administrator." }
         finally { checking = false }
      }
   }

   /** Allocate only a private app-cache original for the camera's temporary URI grant. */
   fun prepareCamera(): File
   {
      checkSession()
      check(!pending && !busy && limits?.mediaTypes?.contains("image/jpeg") == true)
      clearSavedIntent()
      directory.mkdirs()
      saved = false
      message = ""
      return File(directory, "photo-${UUID.randomUUID()}.jpg").also { ownedFiles.add(it); captureFile = it }
   }

   /** A cancelled camera produces no upload; successful originals are hashed without transformation. */
   fun cameraResult(success: Boolean)
   {
      val file = captureFile ?: return
      if(released || !isCurrentSession())
      {
         deleteOwned(file)
         captureFile = null
         return
      }
      if(!success)
      {
         deleteOwned(file)
         captureFile = null
         message = "Photo capture cancelled."
         return
      }
      stage(file, "image/jpeg", "CAMERA")
   }

   /** The selected URI is read immediately under its temporary grant; no broad media permission is needed. */
   fun galleryResult(mediaType: String, open: () -> InputStream?)
   {
      if(pending || busy || released || !isCurrentSession()) return
      val policy = limits ?: return
      if(!mediaType.startsWith("image/") || mediaType !in policy.mediaTypes)
      {
         message = "This photo type is not accepted by the server."
         return
      }
      clearSavedIntent()
      directory.mkdirs()
      val extension = when(mediaType) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "image" }
      val file = File(directory, "photo-${UUID.randomUUID()}.$extension")
      ownedFiles.add(file)
      captureFile = file
      stage(file, mediaType, "GALLERY", open)
   }

   /** Copy/hash with a strict server/local byte bound and delete only this newly created temporary file on failure. */
   private fun stage(file: File, mediaType: String, origin: String, open: (() -> InputStream?)? = null)
   {
      val policy = limits ?: return
      busy = true
      operation = viewModelScope.launch {
         var startCameraUpload = false
         try
         {
            checkSession()
            val staged = withContext(io) {
               if(open != null) (open() ?: error("Photo could not be opened.")).use { input ->
                  copyingInput.set(input)
                  file.outputStream().use { output ->
                     val buffer = ByteArray(65536)
                     var total = 0L
                     while(true)
                     {
                        ensureActive()
                        val count = input.read(buffer)
                        if(count < 0) break
                        total += count
                        require(total <= policy.maximumFileBytes) { "Photo exceeds the server limit." }
                        output.write(buffer, 0, count)
                     }
                  }
                  copyingInput.compareAndSet(input, null)
               }
               require(file.length() in 1..policy.maximumFileBytes) { "Photo is empty or exceeds the server limit." }
               PhotoUploadIntent(target, file, mediaType, origin, file.length(), hash(file))
            }
            checkSession()
            intent = staged
            saved = false
            uploadId = null
            createAttempted = false
            message = if(origin == "CAMERA") "Uploading photo…" else "Photo ready to upload."
            startCameraUpload = origin == "CAMERA"
         }
         catch(exception: CancellationException) { deleteOwned(file); throw exception }
         catch(exception: Exception)
         {
            deleteOwned(file)
            message = "The photo could not be prepared or exceeds the server's size limit. Choose another photo."
         }
         finally { copyingInput.set(null); captureFile = null; busy = false }
         if(startCameraUpload) upload()
      }
   }

   /** Reconnect by recovering the same manifest/status; never allocate a second intent after a lost response. */
   fun upload()
   {
      val original = intent ?: return
      if(busy || saved || released) return
      busy = true
      operation = viewModelScope.launch {
         try
         {
            checkSession()
            var status = recover(original)
            if(status.getString("state") == "COMPLETE") { acceptReceipt(original, status); return@launch }
            require(status.getString("state") in setOf("OPEN", "FAILED", "FINALIZING"))
            if(status.getString("state") != "FINALIZING")
            {
               withContext(io) { require(original.file.length() == original.size && hash(original.file) == original.sha256) }
               val frozen = PhotoUploadLimits.parse(status.getJSONObject("limits"))
               require(original.size <= frozen.maximumFileBytes && original.mediaType in frozen.mediaTypes)
               val items = status.getJSONArray("items")
               require(items.length() == 1 && items.getJSONObject(0).getString("itemId") == original.itemId)
               val received = items.getJSONObject(0).getJSONArray("receivedChunks")
               val known = (0 until received.length()).map { received.getInt(it) }.toSet()
               val count = ((original.size + frozen.chunkBytes - 1) / frozen.chunkBytes).toInt()
               require(known.all { it in 0 until count })
               withContext(io) {
                  RandomAccessFile(original.file, "r").use { file ->
                     for(index in 0 until count)
                     {
                        ensureActive()
                        checkSession()
                        if(index in known) continue
                        val offset = index.toLong() * frozen.chunkBytes
                        val bytes = ByteArray(minOf(frozen.chunkBytes.toLong(), original.size - offset).toInt())
                        file.seek(offset)
                        file.readFully(bytes)
                        status = api.chunk(session, uploadId!!, original.itemId, index, bytes)
                     }
                  }
               }
            }
            checkSession()
            status = api.finalize(session, uploadId!!, original.requestId)
            acceptReceipt(original, status)
         }
         catch(exception: CancellationException) { message = "Upload paused. Check the same upload when connected."; throw exception }
         catch(exception: Exception) { message = "Upload is not confirmed. Retry/check the same upload when connected. Do not select the photo again." }
         finally { busy = false }
      }
   }

   /** Discard recovers an uncertain create before cancelling; it never claims to delete retained originals. */
   fun discard()
   {
      val original = intent ?: return
      if(busy || released) return
      busy = true
      operation = viewModelScope.launch {
         try
         {
            checkSession()
            if(createAttempted)
            {
               val status = recover(original)
               if(status.getString("state") == "COMPLETE") { acceptReceipt(original, status); return@launch }
               val cancelled = api.cancel(session, uploadId!!)
               if(cancelled.getString("state") == "COMPLETE") { acceptReceipt(original, cancelled); return@launch }
               require(cancelled.getString("state") in setOf("CANCELLED", "EXPIRED"))
            }
            deleteOwned(original.file)
            intent = null
            uploadId = null
            createAttempted = false
            message = "Photo discarded."
         }
         catch(exception: CancellationException) { throw exception }
         catch(exception: Exception) { message = "Cancellation is not confirmed. Keep this screen and retry when connected." }
         finally { busy = false }
      }
   }

   /** Backgrounding cancels transport but preserves the exact intent for explicit foreground recovery. */
   fun pause() { operation?.cancel() }

   /** A new capture cannot reactivate a completed intent whose temporary original was already removed. */
   private fun clearSavedIntent()
   {
      if(saved)
      {
         intent = null
         uploadId = null
         createAttempted = false
         saved = false
      }
   }

   /** Recover creation only with the same actor and immutable request; record its server identity immediately. */
   private suspend fun recover(original: PhotoUploadIntent): JSONObject
   {
      checkSession()
      val id = uploadId
      val status = if(id == null)
      {
         createAttempted = true
         api.create(session, original)
      }
      else api.status(session, id)
      val returned = status.getString("uploadId")
      require(UUID.fromString(returned).toString() == returned && (id == null || id == returned))
      uploadId = returned
      return status
   }

   /** A matching COMPLETE receipt alone permits local cleanup and the Saved label. */
   private fun acceptReceipt(original: PhotoUploadIntent, status: JSONObject)
   {
      checkSession()
      require(status.getString("uploadId") == uploadId)
      val receipt = original.verifyReceipt(status)
      saved = true
      deleteOwned(original.file)
      message = if(receipt.getJSONArray("items").getJSONObject(0).getString("lifecycleAtCommit") == "QUARANTINED")
         "Saved photo. Awaiting server review." else "Saved photo."
   }

   /** A rotated Activity is allowed; a changed/expired authenticated session is not. */
   private fun checkSession() { check(!released && isCurrentSession()) { "The photo session has ended." } }

   /** Only files allocated by this instance are eligible for cleanup; the selected gallery original is never owned. */
   private fun deleteOwned(file: File)
   {
      if(file !in ownedFiles) return
      if(file.delete() || !file.exists()) ownedFiles.remove(file)
   }

   /** Destruction differs from rotation/background: cancel I/O, close the active source, then remove our temporary originals. */
   override fun onCleared()
   {
      released = true
      val work = operation
      work?.cancel()
      runCatching { copyingInput.getAndSet(null)?.close() }
      // Completion runs after the copy's use/finally has closed its writer, avoiding delete/write races.
      if(work == null) ownedFiles.toList().forEach(::deleteOwned)
      else work.invokeOnCompletion { ownedFiles.toList().forEach(::deleteOwned) }
      captureFile = null
      intent = null
      uploadId = null
      busy = false
      super.onCleared()
   }

   /** Hash originals using fixed memory, preserving camera/gallery bytes exactly. */
   private fun hash(file: File): String
   {
      val digest = MessageDigest.getInstance("SHA-256")
      file.inputStream().use { input ->
         val buffer = ByteArray(65536)
         while(true)
         {
            val size = input.read(buffer)
            if(size < 0) break
            digest.update(buffer, 0, size)
         }
      }
      return digest.digest().joinToString("") { "%02x".format(it) }
   }
}
