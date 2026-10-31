/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import com.kingsrook.qqq.frontend.android.core.controllers.ANDROID_CLIENT
import com.kingsrook.qqq.frontend.android.core.controllers.RequestOutcome
import com.kingsrook.qqq.frontend.android.core.controllers.RequestOutcomeInterceptor
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiCompatibility
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiException
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/** The semantic widget supplies the parent; the server independently authorizes it on every operation. */
internal data class RecordPhotoTarget(val tableName: String, val recordId: String, val title: String?, val instructions: String?)
{
   companion object
   {
      /** Refuse missing/coerced identities; never derive a parent from an arbitrary form field. */
      fun parse(values: Map<String, Any?>): RecordPhotoTarget
      {
         val parent = values["parent"] as? Map<*, *> ?: error("Missing photo target.")
         val table = parent["tableName"] as? String ?: error("Invalid photo target.")
         val record = parent["recordId"] as? String ?: error("Invalid photo target.")
         require(table.isNotBlank() && table.length <= 200 && record.isNotBlank() && record.length <= 1000)
         require(values["title"] == null || values["title"] is String)
         require(values["instructions"] == null || values["instructions"] is String)
         val title = values["title"] as? String
         require(title == null || title.length <= 255)
         return RecordPhotoTarget(table, record, title, values["instructions"] as? String)
      }
   }
}

/** Frozen server limits are bounded again locally before allocating or reading original bytes. */
internal data class PhotoUploadLimits(val maximumFileBytes: Long, val chunkBytes: Int, val mediaTypes: Set<String>)
{
   companion object
   {
      /** This client sends one original at a time; both per-file and total limits still apply. */
      fun parse(value: JSONObject): PhotoUploadLimits
      {
         val maximum = minOf(value.getLong("maximumFileBytes"), value.getLong("maximumTotalBytes"))
         val chunk = value.getInt("chunkBytes")
         require(maximum in 1..20L * 1024 * 1024 && chunk in 65536..4 * 1024 * 1024 && value.getInt("maximumItems") >= 1)
         val media = value.getJSONArray("acceptedMediaTypes")
         return PhotoUploadLimits(maximum, chunk, (0 until media.length()).map { media.getString(it) }.toSet())
      }
   }
}

/** Immutable intent survives retries; source bytes are private temporary originals, never resized. */
internal data class PhotoUploadIntent(val target: RecordPhotoTarget, val file: File, val mediaType: String,
   val origin: String, val size: Long, val sha256: String, val requestId: String = UUID.randomUUID().toString(),
   val itemId: String = UUID.randomUUID().toString())
{
   /** Replays use exactly this manifest and identity. */
   fun manifest(): JSONObject
   {
      require(origin in setOf("CAMERA", "GALLERY"))
      val item = JSONObject().put("itemId", itemId).put("position", 0).put("fileName", file.name)
         .put("declaredMediaType", mediaType).put("sizeBytes", size).put("sha256", sha256).put("origin", origin)
      return JSONObject().put("requestId", requestId)
         .put("parent", JSONObject().put("tableName", target.tableName).put("recordId", target.recordId))
         .put("items", JSONArray().put(item)).also { target.title?.let { title -> it.put("title", title) } }
   }

   /** Saved requires the complete exact-original association receipt, not only a successful HTTP status. */
   fun verifyReceipt(status: JSONObject): JSONObject
   {
      require(status.getString("state") == "COMPLETE") { "The upload is not complete." }
      val receipt = status.getJSONObject("receipt")
      require(receipt.getString("uploadId") == status.getString("uploadId") && receipt.getString("state") == "COMPLETE")
      require(receipt.getLong("collectionId") > 0)
      Instant.parse(receipt.getString("completedAt"))
      val items = receipt.getJSONArray("items")
      require(items.length() == 1)
      val item = items.getJSONObject(0)
      require(item.getString("itemId") == itemId && item.getInt("position") == 0 && item.getLong("sizeBytes") == size && item.getString("sha256") == sha256)
      require(item.getString("fileId").isNotBlank() && item.getLong("versionId") > 0 && item.getLong("attachmentId") > 0 && item.getString("lifecycleAtCommit").isNotBlank())
      return receipt
   }
}

/** Native Record Files 1.x client. It shares QQQ authentication and the registered semantic extension. */
internal interface RecordFilesTransport
{
   suspend fun capabilities(session: String, target: RecordPhotoTarget): PhotoUploadLimits?
   suspend fun create(session: String, intent: PhotoUploadIntent): JSONObject
   suspend fun status(session: String, uploadId: String): JSONObject
   suspend fun chunk(session: String, uploadId: String, itemId: String, index: Int, bytes: ByteArray): JSONObject
   suspend fun finalize(session: String, uploadId: String, requestId: String): JSONObject
   suspend fun cancel(session: String, uploadId: String): JSONObject
}

/** Transport-only implementation keeps capture and retry lifecycle independent of HTTP. */
internal class RecordFilesApi(private val server: String, private val headers: () -> Map<String, String>,
   private val compatibility: UiApiCompatibility, onOutcome: (RequestOutcome) -> Unit = {}) : RecordFilesTransport
{
   private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
      .callTimeout(30, TimeUnit.SECONDS).addInterceptor(RequestOutcomeInterceptor(onOutcome)).build()
   @Volatile private var captureContractVerified = false

   /** Absence is optional: hide capture actions, while ordinary scanner functionality remains available. */
   override suspend fun capabilities(session: String, target: RecordPhotoTarget): PhotoUploadLimits? = withContext(Dispatchers.IO)
   {
      val version = compatibility.verify()
      val extension = version.extensions.singleOrNull { it.name == "record-files" } ?: return@withContext null
      if(extension.pathPrefix != "/qbit/record-files" || !Regex("1\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(extension.version)) return@withContext null
      val bridge = version.extensions.singleOrNull { it.name == "mobile-record-files" } ?: return@withContext null
      if(bridge.pathPrefix != "/qbit/mobile-record-files" || !supportedVersion(bridge.version)) return@withContext null
      val descriptor = execute(null, Request.Builder().url(server.trimEnd('/') + "/qqq/v1/qbit/mobile-record-files/contract").get())
      if(!supportedVersion(descriptor.getString("version")) || descriptor.getString("blockType") != "RECORD_FILES_CAPTURE" || descriptor.getString("widgetType") != "composite") return@withContext null
      captureContractVerified = true
      val url = endpoint("/capabilities").toHttpUrl().newBuilder().addQueryParameter("tableName", target.tableName).addQueryParameter("recordId", target.recordId).build()
      val value = execute(session, Request.Builder().url(url).get())
      if(!value.getBoolean("canUpload")) return@withContext null
      require(supportedVersion(value.getString("version")) && value.getBoolean("resumable") && !value.getBoolean("offline"))
      PhotoUploadLimits.parse(value.getJSONObject("limits"))
   }

   /** Create is idempotent under the immutable actor/request manifest. */
   override suspend fun create(session: String, intent: PhotoUploadIntent): JSONObject = execute(session,
      mutation("/uploads").post(intent.manifest().toString().toRequestBody(JSON)))

   /** Status is safe after a lost chunk or finalize response and never creates a second upload. */
   override suspend fun status(session: String, uploadId: String): JSONObject = execute(session,
      Request.Builder().url(endpoint("/uploads/${uuid(uploadId)}")).get())

   /** Binary chunks carry exact length and checksum; repeating the same index/bytes is safe. */
   override suspend fun chunk(session: String, uploadId: String, itemId: String, index: Int, bytes: ByteArray): JSONObject
   {
      require(index >= 0 && bytes.isNotEmpty() && bytes.size <= 4 * 1024 * 1024)
      return execute(session, mutation("/uploads/${uuid(uploadId)}/items/${uuid(itemId)}/chunks/$index")
         .header("X-Chunk-SHA256", sha256(bytes)).put(bytes.toRequestBody(BINARY)))
   }

   /** Finalization repeats only with the original request identity. */
   override suspend fun finalize(session: String, uploadId: String, requestId: String): JSONObject = execute(session,
      mutation("/uploads/${uuid(uploadId)}/finalize").header("Idempotency-Key", uuid(requestId)).post(EMPTY))

   /** Cancellation only removes server staging; committed originals remain retained. */
   override suspend fun cancel(session: String, uploadId: String): JSONObject = execute(session,
      mutation("/uploads/${uuid(uploadId)}/cancel").post(EMPTY))

   /** Protocol headers never substitute for the user's current session and verified device. */
   private fun mutation(path: String) = Request.Builder().url(endpoint(path)).header("X-Record-Files-Request", "1")

   /** Routes are fixed contract paths; opaque upload IDs are validated before interpolation. */
   private fun endpoint(path: String) = server.trimEnd('/') + "/qqq/v1/qbit/record-files" + path

   /** Canonical UUID spelling prevents path injection and accidental identity changes. */
   private fun uuid(value: String): String
   {
      require(UUID.fromString(value).toString() == value)
      return value
   }

   /** Cancellation closes transport; bounded JSON parsing cannot allocate an arbitrary server body. */
   private suspend fun execute(session: String?, builder: Request.Builder): JSONObject = withContext(Dispatchers.IO)
   {
      require(session?.isNotBlank() != false && compatibility.server == server)
      val extension = compatibility.verify().extensions.singleOrNull { it.name == "record-files" }
      require(extension != null && extension.pathPrefix == "/qbit/record-files" && supportedVersion(extension.version)) { "Record Files is unavailable on this server." }
      builder.header("X-QQQ-Client", ANDROID_CLIENT).header("Cache-Control", "no-store")
      if(session != null)
      {
         check(captureContractVerified) { "The mobile photo contract has not been verified." }
         builder.header("Cookie", "sessionUUID=$session")
         headers().forEach { (name, value) -> builder.header(name, value) }
      }
      val call = client.newCall(builder.build())
      val response = suspendCancellableCoroutine<Response> { continuation ->
         continuation.invokeOnCancellation { call.cancel() }
         call.enqueue(object : Callback
         {
            override fun onFailure(call: Call, e: IOException) { if(continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { continuation.resume(response) { _, value, _ -> value.close() } }
         })
      }
      response.use {
         if(!it.isSuccessful) throw UiApiException.from(it)
         require(it.code == 200) { "Unexpected upload response status." }
         val source = it.body?.source() ?: error("Missing upload response.")
         source.request(262145)
         require(source.buffer.size <= 262144) { "Oversized upload response." }
         JSONObject(source.readUtf8())
      }
   }

   companion object
   {
      private val JSON = "application/json".toMediaType()
      private val BINARY = "application/octet-stream".toMediaType()
      private val EMPTY: RequestBody = ByteArray(0).toRequestBody(null)

      /** Additive 1.x contracts preserve the six tested ingestion operations. */
      private fun supportedVersion(value: String) = Regex("1\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(value)

      /** Checksums are lowercase SHA-256 as required by the registered ingestion contract. */
      fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
   }
}
