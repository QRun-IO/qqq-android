/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.controllers

import com.kingsrook.qqq.frontend.android.core.model.Environment
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Modern servers return HttpOnly session cookies; both credentials and redirects remain origin-scoped. */
class NetworkQQQRepositoryTest
{
   @Test
   fun clearedOptionalFieldsRemainExplicitNullsOnTheWire() = runBlocking()
   {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(VERSION_FIXTURE))
         server.enqueue(MockResponse().setBody("""{"type":"COMPLETE","processUUID":"process","nextStep":"result","values":{}}"""))
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin)
         repository.setEnvironment(Environment("test", origin))
         repository.processStep("edit", "process", "input", mapOf("notes" to null, "choice" to null, "quantity" to 0, "enabled" to false), 1000)
         assertEquals("/qqq/v1/version", server.takeRequest().path)
         val request = server.takeRequest()
         assertEquals("/qqq/v1/processes/edit/process/step/input", request.path)
         val body = request.body.readUtf8()
         val json = org.json.JSONObject(body.substringAfter("\r\n\r\n").substringBefore("\r\n--"))
         assertTrue("Clearing notes must overwrite the stored value", json.has("notes") && json.isNull("notes"))
         assertTrue("Choosing None must overwrite the stored selection", json.has("choice") && json.isNull("choice"))
         assertEquals(0, json.getInt("quantity"))
         assertFalse(json.getBoolean("enabled"))
      }
   }

   @Test
   fun reportsOnlyStatusAndTimingAndObserverFailuresCannotBreakRequests() = runBlocking()
   {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(VERSION_FIXTURE))
         server.enqueue(MockResponse().setBody("{}"))
         server.enqueue(MockResponse().setResponseCode(503).setBody("private-response-barcode"))
         val outcomes = mutableListOf<RequestOutcome>()
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin, requestObserver = { outcomes.add(it); error("observer failed") })
         repository.setEnvironment(Environment("test", origin))
         repository.setSessionUUID("private-cookie")
         repository.getMetaData("private-query", "1", "test", "1")
         assertTrue(runCatching { repository.getMetaData("test", "1", "test", "1") }.isFailure)
         assertEquals(listOf(200, 200, 503), outcomes.map { it.httpStatus })
         assertTrue(outcomes.all { it.elapsedMillis >= 0 })
         assertFalse(outcomes.toString().contains("private"))
         assertFalse(outcomes.toString().contains("http:"))
      }
   }

   @Test
   fun transportFailureReportsNullStatusWithoutExceptionMessage() = runBlocking()
   {
      val server = MockWebServer()
      server.start()
      val origin = server.url("/").toString()
      server.shutdown()
      val outcomes = mutableListOf<RequestOutcome>()
      val repository = NetworkQQQRepository(origin, requestObserver = { outcomes.add(it) })
      repository.setEnvironment(Environment("test", origin))
      assertTrue(runCatching { repository.getMetaData("test", "1", "test", "1") }.isFailure)
      assertEquals(1, outcomes.size)
      assertNull(outcomes.single().httpStatus)
      assertFalse(outcomes.toString().contains("localhost"))
   }

   @Test
   fun logoutRevokesServerSessionBeforeDroppingTheCookie() = runBlocking()
   {
      MockWebServer().use { server ->
         server.start()
         server.enqueue(MockResponse().setBody(VERSION_FIXTURE))
         server.enqueue(MockResponse().setBody("{}"))
         server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin)
         repository.setEnvironment(Environment("demo", origin))
         repository.setSessionUUID("synthetic-session")
         repository.logout()
         assertEquals("/qqq/v1/version", server.takeRequest().path)
         val logoutRequest = server.takeRequest()
         assertEquals("/qqq/v1/logout", logoutRequest.path)
         assertEquals("POST", logoutRequest.method)
         assertEquals("sessionUUID=synthetic-session", logoutRequest.getHeader("Cookie"))
         repository.getMetaData("test", "1", "test", "1")
         assertNull(server.takeRequest().getHeader("Cookie"))
      }
   }

   @Test
   fun capturesHttpOnlySessionAndDoesNotSendItToAnotherServer() = runBlocking()
   {
      MockWebServer().use { first ->
         MockWebServer().use { second ->
            first.start()
            second.start()
            first.enqueue(MockResponse().setBody(VERSION_FIXTURE))
            first.enqueue(MockResponse().setHeader("Content-Type", "application/json")
               .addHeader("Set-Cookie", "sessionUUID=synthetic-session; HttpOnly; Path=/").setBody("{\"values\":{}}"))
            first.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
            second.enqueue(MockResponse().setBody(VERSION_FIXTURE))
            second.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
            val origin = first.url("/").toString()
            val repository = NetworkQQQRepository(origin) { server ->
               if(server == origin) mapOf("X-QQQ-Device-Id" to "test-device") else emptyMap()
            }
            repository.setEnvironment(Environment("first", origin))
            val login = repository.loginWithPassword("demo", "synthetic-password")
            assertEquals("synthetic-session", login.uuid)
            repository.getMetaData("test", "1", "test", "1")
            assertEquals("/qqq/v1/version", first.takeRequest().path)
            val loginRequest = first.takeRequest()
            assertEquals("/qqq/v1/manageSession", loginRequest.path)
            assertNull(loginRequest.getHeader("Cookie"))
            val nextRequest = first.takeRequest()
            assertEquals("sessionUUID=synthetic-session", nextRequest.getHeader("Cookie"))
            assertNull(nextRequest.getHeader("Authorization"))
            assertEquals("test-device", nextRequest.getHeader("X-QQQ-Device-Id"))
            repository.setEnvironment(Environment("second", second.url("/").toString()))
            repository.getMetaData("test", "1", "test", "1")
            assertEquals("/qqq/v1/version", second.takeRequest().path)
            val otherRequest = second.takeRequest()
            assertNull(otherRequest.getHeader("Cookie"))
            assertNull(otherRequest.getHeader("X-QQQ-Device-Id"))
         }
      }
   }
}
