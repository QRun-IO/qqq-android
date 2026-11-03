/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/*******************************************************************************
 ** Shared real semantic HTTP fixture operations; never prints credentials or response bodies.
 *******************************************************************************/
internal class SessionAcceptanceClient(
   private val origin: String, private val cookie: String,
   private val client: OkHttpClient
) {
   /*******************************************************************************
    **
    *******************************************************************************/
   fun json(path: String, body: JSONObject, expectedStatus: Int = 200): JSONObject = execute(
      Request.Builder().url("$origin$path")
         .header("Cookie", cookie).post(body.toString().toRequestBody("application/json".toMediaType())).build(), expectedStatus
   )

   /*******************************************************************************
    **
    *******************************************************************************/
   fun process(name: String, suffix: String, values: JSONObject): JSONObject {
      var result = execute(
         Request.Builder().url("$origin/qqq/v1/processes/$name$suffix").header("Cookie", cookie)
            .post(
               MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("values", values.toString())
                  .addFormDataPart("stepTimeoutMillis", "10000").build()
            ).build()
      )
      val id = result.optString("processUUID")
      val job = result.optString("jobUUID")
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
      while (result.optString("type") in listOf("JOB_STARTED", "RUNNING")) {
         assertTrue("Fixture process must finish before its deadline", System.nanoTime() < deadline)
         Thread.sleep(50)
         result = execute(Request.Builder().url("$origin/qqq/v1/processes/$name/$id/status/$job").header("Cookie", cookie).build())
      }
      assertTrue("Fixture process must not fail", !result.has("error"))
      return result
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   fun apply(name: String, values: JSONObject) {
      val id = process(name, "/init", JSONObject()).getString("processUUID")
      val applied = process(name, "/$id/step/input", values)
      if (applied.optString("nextStep") == "result") process(name, "/$id/step/result", JSONObject())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   fun sessions(): List<JSONObject> {
      val rows = json("/qqq/v1/table/mobileSession/query", JSONObject()).getJSONArray("records")
      return (0 until rows.length()).map { rows.getJSONObject(it).getJSONObject("values") }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   fun widget(id: String) = json("/qqq/v1/widget/mobileSessionScreen", JSONObject().put("id", id))

   /*******************************************************************************
    **
    *******************************************************************************/
   fun logout() {
      client.newCall(
         Request.Builder().url("$origin/qqq/v1/logout").header("Cookie", cookie)
            .post("{}".toRequestBody("application/json".toMediaType())).build()
      ).execute().use {
         assertTrue("Fixture admin logout must succeed", it.isSuccessful)
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun execute(request: Request, expectedStatus: Int = 200) = client.newCall(request).execute().use {
      assertEquals("Fixture semantic request must succeed", expectedStatus, it.code)
      JSONObject(it.body!!.string())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   companion object {
      /*******************************************************************************
       **
       *******************************************************************************/
      fun login(origin: String, password: String, client: OkHttpClient, account: String = "admin"): String =
         client.newCall(
            Request.Builder().url("$origin/qqq/v1/manageSession")
               .header("Authorization", Credentials.basic(account, password))
               .post("{}".toRequestBody("application/json".toMediaType())).build()
         ).execute().use {
            assertEquals("Fixture admin login must succeed", 200, it.code)
            it.headers("Set-Cookie").first { value -> value.startsWith("sessionUUID=") }.substringBefore(';')
         }

      /*******************************************************************************
       **
       *******************************************************************************/
      fun image(value: Any?): String? = when (value) {
         is String -> value.takeIf { it.startsWith("data:image/jpeg;base64,") }
         is JSONObject -> value.keys().asSequence().mapNotNull { image(value.opt(it)) }.firstOrNull()
         is org.json.JSONArray -> (0 until value.length()).firstNotNullOfOrNull { image(value.opt(it)) }
         else -> null
      }
   }
}
