/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.controllers

import com.kingsrook.qqq.frontend.android.core.model.Environment
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Modern servers return HttpOnly session cookies; both credentials and redirects remain origin-scoped. */
class NetworkQQQRepositoryTest
{
   @Test
   fun logoutRevokesServerSessionBeforeDroppingTheCookie() = runBlocking()
   {
      MockWebServer().use { server ->
         server.start()
         server.enqueue(MockResponse().setBody("{}"))
         server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin)
         repository.setEnvironment(Environment("demo", origin))
         repository.setSessionUUID("synthetic-session")
         repository.logout()
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
            first.enqueue(MockResponse().setHeader("Content-Type", "application/json")
               .addHeader("Set-Cookie", "sessionUUID=synthetic-session; HttpOnly; Path=/").setBody("{\"values\":{}}"))
            first.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
            second.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
            val origin = first.url("/").toString()
            val repository = NetworkQQQRepository(origin) { server ->
               if(server == origin) mapOf("X-QQQ-Device-Id" to "test-device") else emptyMap()
            }
            repository.setEnvironment(Environment("first", origin))
            val login = repository.loginWithPassword("demo", "synthetic-password")
            assertEquals("synthetic-session", login.uuid)
            repository.getMetaData("test", "1", "test", "1")
            val loginRequest = first.takeRequest()
            assertEquals("/qqq/v1/manageSession", loginRequest.path)
            assertNull(loginRequest.getHeader("Cookie"))
            val nextRequest = first.takeRequest()
            assertEquals("sessionUUID=synthetic-session", nextRequest.getHeader("Cookie"))
            assertNull(nextRequest.getHeader("Authorization"))
            assertEquals("test-device", nextRequest.getHeader("X-QQQ-Device-Id"))
            repository.setEnvironment(Environment("second", second.url("/").toString()))
            repository.getMetaData("test", "1", "test", "1")
            val otherRequest = second.takeRequest()
            assertNull(otherRequest.getHeader("Cookie"))
            assertNull(otherRequest.getHeader("X-QQQ-Device-Id"))
         }
      }
   }
}
