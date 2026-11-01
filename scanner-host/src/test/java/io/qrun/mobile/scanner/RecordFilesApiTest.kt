/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import com.kingsrook.qqq.frontend.android.core.controllers.UiApiCompatibility
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*******************************************************************************
 ** Verify discovery, immutable request replay and exact persistence receipts without a device.
 *******************************************************************************/
class RecordFilesApiTest {
   private val target = RecordPhotoTarget("document", "opaque/key", "Photos", null)
   private val bytes = byteArrayOf(1, 2, 3)

   /*******************************************************************************
    ** Optional missing extensions must not send any authenticated upload traffic.
    *******************************************************************************/
   @Test
   fun absentExtensionHidesCaptureWithoutSendingCredentials() = runBlocking()
   {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(version(false)))
         val origin = server.url("/").toString()
         val api = RecordFilesApi(origin, { mapOf("X-QQQ-Device-Token" to "synthetic") }, UiApiCompatibility(origin))
         assertNull(api.capabilities("synthetic-session", target))
         val discovery = server.takeRequest()
         assertEquals("/qqq/v1/version", discovery.path)
         assertNull(discovery.getHeader("Cookie"))
         assertNull(discovery.getHeader("X-QQQ-Device-Token"))
         assertEquals(1, server.requestCount)
      }
   }

   /*******************************************************************************
    ** Incompatible mobile vocabulary stops before record capabilities or mutations receive credentials.
    *******************************************************************************/
   @Test
   fun unknownCaptureVocabularyNeverSendsCredentials() = runBlocking()
   {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(version(true)))
         server.enqueue(MockResponse().setBody("""{"version":"2.0.0","blockType":"RECORD_FILES_CAPTURE","widgetType":"composite"}"""))
         val origin = server.url("/").toString()
         val api = RecordFilesApi(origin, { mapOf("X-QQQ-Device-Token" to "synthetic") }, UiApiCompatibility(origin))
         assertNull(api.capabilities("synthetic-session", target))
         repeat(2) { assertNull(server.takeRequest().getHeader("Cookie")) }
         assertEquals(2, server.requestCount)
         assertTrue(runCatching { api.create("synthetic-session", intent()) }.isFailure)
         assertEquals(2, server.requestCount)
      }
   }

   /*******************************************************************************
    ** Repeated create/finalize use the same intent; chunks carry exact byte count/checksum and device identity.
    *******************************************************************************/
   @Test
   fun requestsUseRegisteredRoutesAndStableIdentity() = runBlocking()
   {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(version(true)))
         server.enqueue(MockResponse().setBody("""{"version":"1.0.0","blockType":"RECORD_FILES_CAPTURE","widgetType":"composite"}"""))
         server.enqueue(
            MockResponse().setBody(
               JSONObject().put("version", "1.0.0").put("canUpload", true)
                  .put("resumable", true).put("offline", false).put("limits", limits()).toString()
            )
         )
         repeat(5) { server.enqueue(MockResponse().setBody("{}")) }
         val origin = server.url("/").toString()
         val api = RecordFilesApi(origin, { mapOf("X-QQQ-Device-Token" to "synthetic") }, UiApiCompatibility(origin))
         assertNotNull(api.capabilities("synthetic-session", target))
         val intent = intent()
         api.create("synthetic-session", intent)
         api.create("synthetic-session", intent)
         val upload = UUID.randomUUID().toString()
         api.chunk("synthetic-session", upload, intent.itemId, 0, bytes)
         api.finalize("synthetic-session", upload, intent.requestId)
         api.status("synthetic-session", upload)
         server.takeRequest()
         val descriptor = server.takeRequest()
         assertEquals("/qqq/v1/qbit/mobile-record-files/contract", descriptor.path)
         assertNull(descriptor.getHeader("Cookie"))
         assertNull(descriptor.getHeader("X-QQQ-Device-Token"))
         val capabilities = server.takeRequest()
         assertEquals("opaque/key", capabilities.requestUrl!!.queryParameter("recordId"))
         assertEquals("sessionUUID=synthetic-session", capabilities.getHeader("Cookie"))
         val first = server.takeRequest()
         val second = server.takeRequest()
         assertEquals(first.body.readUtf8(), second.body.readUtf8())
         assertEquals("1", first.getHeader("X-Record-Files-Request"))
         assertEquals("android/qqq-mobile", first.getHeader("X-QQQ-Client"))
         assertEquals("synthetic", first.getHeader("X-QQQ-Device-Token"))
         val chunk = server.takeRequest()
         assertEquals("PUT", chunk.method)
         assertEquals("3", chunk.getHeader("Content-Length"))
         assertEquals(RecordFilesApi.sha256(bytes), chunk.getHeader("X-Chunk-SHA256"))
         assertArrayEquals(bytes, chunk.body.readByteArray())
         val finalize = server.takeRequest()
         assertEquals(intent.requestId, finalize.getHeader("Idempotency-Key"))
         assertEquals(0L, finalize.bodySize)
         assertEquals("GET", server.takeRequest().method)
      }
   }

   /*******************************************************************************
    ** A partial, mismatched or weak receipt must never display Saved.
    *******************************************************************************/
   @Test
   fun receiptRequiresEveryExactOriginalIdentity() {
      val intent = intent()
      val status = complete(intent)
      assertEquals("COMPLETE", intent.verifyReceipt(status).getString("state"))
      val variants = listOf<(JSONObject) -> Unit>(
         { it.put("state", "FINALIZING") },
         { it.getJSONObject("receipt").put("state", "FINALIZING") },
         { it.getJSONObject("receipt").put("uploadId", UUID.randomUUID().toString()) },
         { saved(it).put("itemId", UUID.randomUUID().toString()) },
         { saved(it).put("sha256", "0".repeat(64)) },
         { saved(it).put("sizeBytes", 4) },
         { saved(it).put("position", 1) },
         { saved(it).put("attachmentId", 0) },
         { saved(it).put("fileId", "") },
         { it.getJSONObject("receipt").put("items", JSONArray()) },
      )
      variants.forEach { change ->
         val altered = JSONObject(status.toString())
         change(altered)
         assertTrue(runCatching { intent.verifyReceipt(altered) }.isFailure)
      }
   }

   /*******************************************************************************
    ** The server cannot expand allocations beyond the supported per-original/chunk bounds.
    *******************************************************************************/
   @Test
   fun limitsAndParentMetadataFailClosed() {
      assertEquals(65536, PhotoUploadLimits.parse(limits()).chunkBytes)
      assertTrue(runCatching { PhotoUploadLimits.parse(limits().put("chunkBytes", 32 * 1024 * 1024)) }.isFailure)
      assertTrue(runCatching { PhotoUploadLimits.parse(limits().put("maximumItems", 0)) }.isFailure)
      assertTrue(runCatching { RecordPhotoTarget.parse(mapOf("parent" to mapOf("tableName" to "document", "recordId" to 1))) }.isFailure)
   }

   /*******************************************************************************
    ** Synthetic originals do not read or write user files.
    *******************************************************************************/
   private fun intent() = PhotoUploadIntent(target, File("photo.jpg"), "image/jpeg", "CAMERA", bytes.size.toLong(), RecordFilesApi.sha256(bytes))

   /*******************************************************************************
    ** A minimal complete response follows the frozen extension schema.
    *******************************************************************************/
   private fun complete(intent: PhotoUploadIntent): JSONObject {
      val upload = UUID.randomUUID().toString()
      val item = JSONObject().put("itemId", intent.itemId).put("fileId", "file-key").put("versionId", 1)
         .put("attachmentId", 2).put("sizeBytes", intent.size).put("sha256", intent.sha256).put("position", 0).put("lifecycleAtCommit", "QUARANTINED")
      val receipt = JSONObject().put("uploadId", upload).put("collectionId", 3).put("state", "COMPLETE")
         .put("completedAt", "2026-10-31T12:00:00Z").put("items", JSONArray().put(item))
      return JSONObject().put("uploadId", upload).put("state", "COMPLETE").put("receipt", receipt)
   }

   /*******************************************************************************
    ** Locate the only synthetic receipt item for independent corruptions.
    *******************************************************************************/
   private fun saved(status: JSONObject) = status.getJSONObject("receipt").getJSONArray("items").getJSONObject(0)

   /*******************************************************************************
    ** Small valid limits keep transport tests cheap.
    *******************************************************************************/
   private fun limits() = JSONObject().put("maximumFileBytes", 1024).put("maximumTotalBytes", 1024).put("maximumItems", 1)
      .put("chunkBytes", 65536).put("lifetimeSeconds", 60).put("acceptedMediaTypes", JSONArray().put("image/jpeg"))

   /*******************************************************************************
    ** Discovery remains credential-free even for an absent optional extension.
    *******************************************************************************/
   private fun version(recordFiles: Boolean): String = JSONObject().put("apiVersion", "1.8.0")
      .put("features", JSONArray(listOf("version", "metaData", "processes", "processCancel", "extensions", "widgets")))
      .put("clientChannels", JSONArray().put("android"))
      .put("extensions", JSONArray().also {
         if (recordFiles) {
            it.put(JSONObject().put("name", "record-files").put("version", "1.0.0").put("pathPrefix", "/qbit/record-files"))
            it.put(JSONObject().put("name", "mobile-record-files").put("version", "1.0.0").put("pathPrefix", "/qbit/mobile-record-files"))
         }
      }).toString()
}
