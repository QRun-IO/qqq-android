/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.controllers

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

const val ANDROID_CLIENT = "android/qqq-mobile"
const val MOBILE_SCANNERS_PATH = "/qqq/v1/qbit/mobile-scanners"

@Serializable
data class UiApiExtension(val name: String, val version: String, val pathPrefix: String)

@Serializable
data class UiApiVersion(val apiVersion: String, val features: List<String> = emptyList(), val extensions: List<UiApiExtension> = emptyList(),
   val clientChannels: List<String> = emptyList())

class UiApiCompatibilityException(message: String) : IOException(message)

/** One origin and one host lifetime. Discovery uses a separate client with no credentials or cookies. */
class UiApiCompatibility(val server: String, private val requireMobileScanners: Boolean = false,
   private val observer: (RequestOutcome) -> Unit = {})
{
   private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
      .callTimeout(5, TimeUnit.SECONDS).addInterceptor(RequestOutcomeInterceptor(observer)).build()
   @Volatile private var verified: UiApiVersion? = null

   /** Call on an IO thread. Synchronization prevents competing startup requests bypassing discovery. */
   @Synchronized
   fun verify(): UiApiVersion
   {
      verified?.let { return it }
      val request = Request.Builder().url(server.trimEnd('/') + "/qqq/v1/version")
         .header("X-QQQ-Client", ANDROID_CLIENT).header("Cache-Control", "no-store").build()
      val version = client.newCall(request).execute().use { response ->
         if(!response.isSuccessful) throw UiApiCompatibilityException("This server does not provide a supported QQQ UI API. Ask your administrator to upgrade the server.")
         val source = response.body?.source()
         source?.request(65537)
         val bytes = source?.buffer?.readByteArray(minOf(source.buffer.size, 65537)) ?: byteArrayOf()
         if(bytes.size > 65536) throw UiApiCompatibilityException("The server returned invalid QQQ UI API version information.")
         try { Json { ignoreUnknownKeys = true; coerceInputValues = true }.decodeFromString<UiApiVersion>(bytes.toString(Charsets.UTF_8)) }
         catch(exception: Exception) { throw UiApiCompatibilityException("The server returned invalid QQQ UI API version information.") }
      }
      validate(version, requireMobileScanners)
      verified = version
      return version
   }

   companion object
   {
      // Compiled and tested against the published 1.8.0 contract; additive 1.x versions remain compatible.
      const val SUPPORTED_CORE_RANGE = ">=1.8.0 <2.0.0"
      const val SUPPORTED_MOBILE_RANGE = ">=1.0.0 <2.0.0"
      val REQUIRED_FEATURES = setOf("version", "metaData", "processes", "processCancel", "extensions", "widgets")

      fun validate(version: UiApiVersion, requireMobileScanners: Boolean)
      {
         fun supported(value: String, minMinor: Int): Boolean
         {
            val parts = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matchEntire(value)?.groupValues ?: return false
            return parts[1] == "1" && (parts[2].toIntOrNull() ?: return false) >= minMinor && parts[3].toIntOrNull() != null
         }
         if(!supported(version.apiVersion, 8)) throw UiApiCompatibilityException("Unsupported QQQ UI API version. This app requires $SUPPORTED_CORE_RANGE. Update the app or ask your administrator to upgrade the server.")
         if(!version.features.containsAll(REQUIRED_FEATURES)) throw UiApiCompatibilityException("This server is missing required QQQ UI API features. Ask your administrator to upgrade the server.")
         if("android" !in version.clientChannels) throw UiApiCompatibilityException("This server does not support the Android client channel. Ask your administrator to enable the android channel.")
         if(requireMobileScanners)
         {
            val extension = version.extensions.singleOrNull { it.name == "mobile-scanners" }
               ?: throw UiApiCompatibilityException("This server is missing the mobile-scanners extension. Ask your administrator to install it.")
            if(!supported(extension.version, 0) || extension.pathPrefix != "/qbit/mobile-scanners")
               throw UiApiCompatibilityException("Unsupported mobile-scanners extension. This app requires $SUPPORTED_MOBILE_RANGE at /qbit/mobile-scanners. Update the app or server extension.")
         }
      }
   }
}
