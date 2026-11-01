/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.controllers

import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.NativeLogoutResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

/** Exercise the actual additive wire operation, cookie isolation and secret-safe diagnostics. */
class NativeLogoutRepositoryTest
{
   private val version = VERSION_FIXTURE.replace("1.8.0", "1.10.0").replace("\"widgets\"", "\"widgets\",\"nativeSessionLogout\"")
   private val callback = "io.qrun.test.oauth://callback/oauth2redirect"
   private val response = """{"nativeLogoutData":{"issuer":"https://identity.example","endSessionEndpoint":"https://identity.example/end-session","postLogoutRedirectUri":"$callback","idTokenHint":"synthetic.logout.hint.canary"}}"""

   @Test fun explicitLogoutReturnsTransientHintAndCannotAdoptResponseCookies() = runBlocking()
   {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(version))
         server.enqueue(MockResponse().setBody(response).setHeader("Set-Cookie", "sessionUUID=must-not-adopt; Path=/"))
         server.enqueue(MockResponse().setBody("{}"))
         val events = mutableListOf<RequestOutcome>()
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin, requestObserver = { events.add(it) })
         repository.setEnvironment(Environment("test", origin))
         repository.setSessionUUID("synthetic-session")
         val result = repository.logoutWithProvider(callback)
         assertEquals("synthetic.logout.hint.canary", result?.idTokenHint)
         assertFalse(result.toString().contains("canary"))
         assertFalse(Json.decodeFromString<NativeLogoutResponse>(response).toString().contains("canary"))
         assertFalse(events.toString().contains("canary"))
         assertEquals("/qqq/v1/version", server.takeRequest().path)
         val logout = server.takeRequest()
         assertEquals("/qqq/v1/logout", logout.path)
         assertEquals("POST", logout.method)
         assertEquals("sessionUUID=synthetic-session", logout.getHeader("Cookie"))
         assertEquals("""{"nativeLogout":{"postLogoutRedirectUri":"$callback"}}""", logout.body.readUtf8())
         repository.getMetaData("test", "1", "test", "1")
         assertNull(server.takeRequest().getHeader("Cookie"))
      }
   }

   @Test fun oldServerReceivesOnlyOrdinaryLogoutAndNoOptionalBody() = runBlocking()
   {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(VERSION_FIXTURE))
         server.enqueue(MockResponse().setBody("{}"))
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin)
         repository.setEnvironment(Environment("test", origin))
         repository.setSessionUUID("synthetic-session")
         assertNull(repository.logoutWithProvider(callback))
         server.takeRequest()
         val request = server.takeRequest()
         assertEquals("/qqq/v1/logout", request.path)
         assertEquals(0L, request.bodySize)
         assertEquals("sessionUUID=synthetic-session", request.getHeader("Cookie"))
      }
   }

   @Test fun failureAndMalformedMaterialDropCookiesWithoutEmittingResponseSecrets() = runBlocking()
   {
      for(reply in listOf(MockResponse().setResponseCode(503).setBody("""{"message":"synthetic.logout.hint.canary"}"""),
         MockResponse().setBody("""{"nativeLogoutData":{"idTokenHint":"synthetic.logout.hint.canary"}}""")))
      {
         MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(version))
            server.enqueue(reply)
            server.enqueue(MockResponse().setBody("{}"))
            val origin = server.url("/").toString()
            val repository = NetworkQQQRepository(origin)
            repository.setEnvironment(Environment("test", origin))
            repository.setSessionUUID("synthetic-session")
            val outcome = runCatching { repository.logoutWithProvider(callback) }
            assertNull(outcome.getOrNull())
            assertFalse(outcome.exceptionOrNull().toString().contains("canary"))
            repository.getMetaData("test", "1", "test", "1")
            server.takeRequest(); server.takeRequest()
            assertNull(server.takeRequest().getHeader("Cookie"))
         }
      }
   }

   @Test fun logoutRequiresBothVersionAndFeature()
   {
      for(value in listOf(UiApiVersion("1.9.0", listOf("nativeSessionLogout")), UiApiVersion("1.10.0"),
         UiApiVersion("2.0.0", listOf("nativeSessionLogout"))))
         assertTrue(runCatching { UiApiCompatibility.validateNativeSessionLogout(value) }.isFailure)
      UiApiCompatibility.validateNativeSessionLogout(UiApiVersion("1.10.0", listOf("nativeSessionLogout")))
   }

   @Test fun lateResponseRemainsScopedToOriginalServerWithoutClearingNewServerCookie() = runBlocking()
   {
      MockWebServer().use { first -> MockWebServer().use { second ->
         first.enqueue(MockResponse().setBody(version))
         first.enqueue(MockResponse().setBody(response).setBodyDelay(150, java.util.concurrent.TimeUnit.MILLISECONDS))
         second.enqueue(MockResponse().setBody(VERSION_FIXTURE))
         second.enqueue(MockResponse().setBody("{}"))
         val origin = first.url("/").toString()
         val repository = NetworkQQQRepository(origin)
         repository.setEnvironment(Environment("first", origin))
         repository.setSessionUUID("old-session")
         val pending = async(Dispatchers.IO) { repository.logoutWithProvider(callback) }
         first.takeRequest(); first.takeRequest()
         repository.setEnvironment(Environment("second", second.url("/").toString()))
         repository.setSessionUUID("new-session")
         pending.await()
         repository.getMetaData("test", "1", "test", "1")
         second.takeRequest()
         assertEquals("sessionUUID=new-session", second.takeRequest().getHeader("Cookie"))
      } }
   }
}
