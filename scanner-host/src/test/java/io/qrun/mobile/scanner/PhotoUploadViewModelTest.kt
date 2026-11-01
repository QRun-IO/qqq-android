/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import java.io.IOException
import java.util.UUID
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/*******************************************************************************
 ** Lifecycle and lost-response tests preserve the actual original and immutable server identity.
 *******************************************************************************/
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoUploadViewModelTest {
   @get:Rule
   val files = TemporaryFolder()
   private val target = RecordPhotoTarget("document", "1", null, null)
   private val original = byteArrayOf(1, 2, 3, 4)

   /*******************************************************************************
    ** Returning from the camera saves automatically, with no second upload action.
    *******************************************************************************/
   @Test
   fun successfulCameraCaptureAutomaticallyUploadsExactOriginal() = runTest()
   {
      val dispatcher = StandardTestDispatcher(testScheduler)
      Dispatchers.setMain(dispatcher)
      try {
         val transport = FakeTransport()
         val model = PhotoUploadViewModel(transport, "session", target, files.newFolder(), dispatcher) { true }
         advanceUntilIdle()
         model.prepareCamera().writeBytes(original)
         model.cameraResult(true)
         advanceUntilIdle()
         assertTrue(model.saved)
         assertEquals(1, transport.creates.size)
         assertArrayEquals(original, transport.uploaded)
         assertEquals("CAMERA", model.intent!!.origin)
      } finally {
         Dispatchers.resetMain()
      }
   }

   /*******************************************************************************
    ** Clearing the process cancels in-flight transport before removing only the app-owned temporary original.
    *******************************************************************************/
   @Test
   fun destructionCleansPrivateOriginalWithoutClaimingServerCancellation() = runTest()
   {
      val dispatcher = StandardTestDispatcher(testScheduler)
      Dispatchers.setMain(dispatcher)
      try {
         val transport = FakeTransport().apply { chunkGate = CompletableDeferred() }
         val model = PhotoUploadViewModel(transport, "session", target, files.newFolder(), dispatcher) { true }
         val store = ViewModelStore()
         ViewModelProvider(store, object : ViewModelProvider.Factory {
            /*******************************************************************************
             **
             *******************************************************************************/
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = model as T
         })[PhotoUploadViewModel::class.java]
         advanceUntilIdle()
         model.galleryResult("image/jpeg") { original.inputStream() }
         advanceUntilIdle()
         val file = model.intent!!.file
         model.upload()
         runCurrent()
         assertTrue(model.busy)
         store.clear()
         advanceUntilIdle()
         assertFalse(file.exists())
         assertEquals(0, transport.cancels)
         assertFalse(model.saved)
         model.galleryResult("image/jpeg") { original.inputStream() }
         model.cameraResult(true)
         advanceUntilIdle()
         assertNull(model.intent)
         assertFalse(model.pending)
         assertEquals(1, transport.creates.size)
      } finally {
         Dispatchers.resetMain()
      }
   }

   /*******************************************************************************
    ** A lost create response recovers the same request, then a lost finalize is checked without duplicate finalization.
    *******************************************************************************/
   @Test
   fun lostResponsesKeepIdentityAndSavedRequiresReceipt() = runTest()
   {
      val dispatcher = StandardTestDispatcher(testScheduler)
      Dispatchers.setMain(dispatcher)
      try {
         val transport = FakeTransport().apply { loseCreate = true; loseFinalize = true }
         val model = PhotoUploadViewModel(transport, "session", target, files.newFolder(), dispatcher) { true }
         advanceUntilIdle()
         model.galleryResult("image/jpeg") { original.inputStream() }
         advanceUntilIdle()
         val intent = model.intent!!
         model.upload()
         advanceUntilIdle()
         assertFalse(model.saved)
         assertTrue(model.pending)
         assertEquals(intent.requestId, model.intent!!.requestId)
         model.upload()
         advanceUntilIdle()
         assertFalse(model.saved)
         assertEquals(2, transport.creates.size)
         assertEquals(transport.creates.first(), transport.creates.last())
         assertArrayEquals(original, transport.uploaded)
         model.upload()
         advanceUntilIdle()
         assertTrue(model.saved)
         assertFalse(model.pending)
         assertFalse(intent.file.exists())
         assertEquals(1, transport.finalizes)
         model.prepareCamera()
         model.cameraResult(false)
         assertFalse(model.pending)
      } finally {
         Dispatchers.resetMain()
      }
   }

   /*******************************************************************************
    ** Leaving the foreground cancels transport while retaining the original; a new actor cannot resume it.
    *******************************************************************************/
   @Test
   fun backgroundPauseRetainsOriginalAndChangedSessionCannotResume() = runTest()
   {
      val dispatcher = StandardTestDispatcher(testScheduler)
      Dispatchers.setMain(dispatcher)
      try {
         var current = true
         val transport = FakeTransport().apply { chunkGate = CompletableDeferred() }
         val model = PhotoUploadViewModel(transport, "session", target, files.newFolder(), dispatcher) { current }
         advanceUntilIdle()
         model.galleryResult("image/jpeg") { original.inputStream() }
         advanceUntilIdle()
         val intent = model.intent!!
         model.upload()
         runCurrent()
         assertTrue(model.busy)
         model.pause()
         runCurrent()
         assertFalse(model.busy)
         assertTrue(intent.file.exists())
         assertTrue(model.pending)
         val calls = transport.statusCalls
         current = false
         model.upload()
         advanceUntilIdle()
         assertEquals(calls, transport.statusCalls)
         assertFalse(model.saved)
      } finally {
         Dispatchers.resetMain()
      }
   }

   /*******************************************************************************
    ** Discard after an uncertain create first recovers that intent, then confirms server staging cancellation.
    *******************************************************************************/
   @Test
   fun discardRecoversUncertainCreateBeforeRemovingLocalOriginal() = runTest()
   {
      val dispatcher = StandardTestDispatcher(testScheduler)
      Dispatchers.setMain(dispatcher)
      try {
         val transport = FakeTransport().apply { loseCreate = true }
         val model = PhotoUploadViewModel(transport, "session", target, files.newFolder(), dispatcher) { true }
         advanceUntilIdle()
         model.galleryResult("image/jpeg") { original.inputStream() }
         advanceUntilIdle()
         val file = model.intent!!.file
         model.upload()
         advanceUntilIdle()
         model.discard()
         advanceUntilIdle()
         assertEquals(2, transport.creates.size)
         assertEquals(1, transport.cancels)
         assertFalse(file.exists())
         assertFalse(model.pending)
      } finally {
         Dispatchers.resetMain()
      }
   }

   /*******************************************************************************
    ** An in-memory server retains state independently from simulated response loss.
    *******************************************************************************/
   private class FakeTransport : RecordFilesTransport {
      val id = UUID.randomUUID().toString()
      val creates = mutableListOf<String>()
      var uploaded: ByteArray? = null
      var finalizes = 0
      var cancels = 0
      var statusCalls = 0
      var loseCreate = false
      var loseFinalize = false
      var chunkGate: CompletableDeferred<Unit>? = null
      private lateinit var intent: PhotoUploadIntent
      private var state = "OPEN"

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun capabilities(session: String, target: RecordPhotoTarget) = PhotoUploadLimits(1024, 65536, setOf("image/jpeg"))

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun create(session: String, intent: PhotoUploadIntent): JSONObject {
         this.intent = intent
         creates.add(intent.manifest().toString())
         if (loseCreate) {
            loseCreate = false; throw IOException("Response lost")
         }
         return response()
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun status(session: String, uploadId: String): JSONObject {
         statusCalls++; return response()
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun chunk(session: String, uploadId: String, itemId: String, index: Int, bytes: ByteArray): JSONObject {
         chunkGate?.await()
         uploaded = bytes.copyOf()
         return response()
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun finalize(session: String, uploadId: String, requestId: String): JSONObject {
         finalizes++
         state = "COMPLETE"
         if (loseFinalize) {
            loseFinalize = false; throw IOException("Response lost")
         }
         return response()
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      override suspend fun cancel(session: String, uploadId: String): JSONObject {
         cancels++; state = "CANCELLED"; return response()
      }

      /*******************************************************************************
       ** Frozen status includes the exact original receipt only after commit.
       *******************************************************************************/
      private fun response(): JSONObject {
         val receipt = if (state == "COMPLETE") JSONObject().put("uploadId", id).put("state", "COMPLETE").put("collectionId", 1)
            .put("completedAt", "2026-10-31T12:00:00Z").put(
               "items", JSONArray().put(
                  JSONObject()
                     .put("itemId", intent.itemId).put("position", 0).put("fileId", "file").put("versionId", 2).put("attachmentId", 3)
                     .put("sha256", intent.sha256).put("sizeBytes", intent.size).put("lifecycleAtCommit", "QUARANTINED")
               )
            ) else JSONObject.NULL
         return JSONObject().put("uploadId", id).put("state", state).put("receipt", receipt)
            .put(
               "limits", JSONObject().put("maximumFileBytes", 1024).put("maximumTotalBytes", 1024).put("maximumItems", 1)
                  .put("chunkBytes", 65536).put("acceptedMediaTypes", JSONArray().put("image/jpeg"))
            )
            .put(
               "items", JSONArray().put(
                  JSONObject().put("itemId", intent.itemId)
                     .put("receivedChunks", JSONArray().also { if (uploaded != null) it.put(0) })
               )
            )
      }
   }
}
