/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.controllers

import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionRequest
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.OAuth2AuthenticationMetaData
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class NativeSessionLoginTest
{
   private val version = VERSION_FIXTURE.replace("1.8.0", "1.10.0").replace("\"widgets\"", "\"widgets\",\"nativeSessionLogin\",\"nativeSessionNonceValidation\"")
   private val request = ManageSessionRequest(code = "synthetic-code", codeVerifier = "v".repeat(64),
      redirectUri = "io.qrun.mobile.scanner.oauth://callback/oauth2redirect",
      authenticationFlow = OAuth2AuthenticationMetaData.SESSION_FLOW, expectedNonce = "original-nonce-canary-1234")

   @Test fun metadataKeepsNativeBearerDistinctFromAdvertisedSessionFlow()
   {
      val metadata = Json.decodeFromString<BaseAuthenticationMetaData>("""{"type":"OAUTH2","name":"organization","values":{"clientId":"server-client","baseUrl":"https://id.example/discovery-base/","scopes":"openid profile","nativeClientId":"public-bearer-client","nativeSessionLogin":{"flow":"AUTHORIZATION_CODE_PKCE_SESSION","codeChallengeMethod":"S256","prompt":"login","nonceValidation":"REQUIRED","issuer":"https://id.example/","discoveryUrl":"https://id.example/application/o/company/.well-known/openid-configuration","redirectUris":["io.qrun.mobile.scanner.oauth://callback/oauth2redirect"]}}}""") as OAuth2AuthenticationMetaData
      assertEquals("server-client", metadata.values.clientId)
      assertEquals("public-bearer-client", metadata.values.nativeClientId)
      assertEquals("https://id.example/", metadata.values.nativeSessionLogin!!.issuer)
      assertEquals("login", metadata.values.nativeSessionLogin!!.prompt)
      assertEquals("REQUIRED", metadata.values.nativeSessionLogin!!.nonceValidation)
      assertNull((Json.decodeFromString<BaseAuthenticationMetaData>("""{"type":"OAUTH2","name":"old","values":{"clientId":"server-client"}}""") as OAuth2AuthenticationMetaData).values.nativeSessionLogin)
      val wire = Json.encodeToString(request)
      assertTrue(wire.contains("\"authenticationFlow\":\"AUTHORIZATION_CODE_PKCE_SESSION\""))
      assertTrue(wire.contains("\"expectedNonce\":\"original-nonce-canary-1234\""))
      assertFalse(wire.contains("accessToken"))
      assertFalse(wire.contains("uuid"))
      assertFalse(request.toString().contains("synthetic-code"))
      assertFalse(request.toString().contains("v".repeat(64)))
      assertFalse(request.toString().contains("original-nonce-canary"))
      assertFalse(Json.encodeToString(ManageSessionRequest(accessToken = "synthetic-legacy-token")).contains("expectedNonce"))
   }

   @Test fun featureAndVersionAreRequiredTogether()
   {
      val supported = Json.decodeFromString<UiApiVersion>(version)
      UiApiCompatibility.validateNativeSessionLogin(supported)
      for(unsupported in listOf(supported.copy(apiVersion = "1.8.9"), supported.copy(apiVersion = "1.9.0"), supported.copy(apiVersion = "2.0.0"),
         supported.copy(apiVersion = "1.10.0-preview"), supported.copy(features = supported.features - "nativeSessionLogin"),
         supported.copy(features = supported.features - "nativeSessionNonceValidation")))
         assertTrue(runCatching { UiApiCompatibility.validateNativeSessionLogin(unsupported) }.exceptionOrNull() is UiApiCompatibilityException)
   }

   @Test fun exchangeCookieIsDeferredAndDiscardNeverChangesCurrentSession() = runBlocking {
      MockWebServer().use { server ->
         val origin = server.url("/").toString()
         server.enqueue(MockResponse().setBody(version))
         server.enqueue(MockResponse().setBody("{}").addHeader("Set-Cookie", "sessionUUID=new-login; Path=/; HttpOnly"))
         server.enqueue(MockResponse().setBody("{}").addHeader("Set-Cookie", "sessionUUID=; Path=/; Max-Age=0"))
         server.enqueue(MockResponse().setBody("""{"type":"TABLE_BASED","name":"users","values":{}}"""))
         val repository = NetworkQQQRepository(origin)
         repository.setEnvironment(Environment("fixture", origin))
         repository.setSessionUUID("current-session")
         val response = repository.exchangeNativeSession(request)
         assertEquals("new-login", response.uuid)
         repository.discardNativeSession(origin, response.uuid!!)
         repository.getAuthenticationMetaData()
         assertEquals("/qqq/v1/version", server.takeRequest().path)
         val exchange = server.takeRequest()
         assertEquals("/qqq/v1/manageSession", exchange.path)
         assertNull(exchange.getHeader("Cookie"))
         val body = Json.parseToJsonElement(exchange.body.readUtf8()).toString()
         assertTrue(body.contains("authenticationFlow"))
         assertTrue(body.contains("\"expectedNonce\":\"original-nonce-canary-1234\""))
         val discard = server.takeRequest()
         assertEquals("/qqq/v1/logout", discard.path)
         assertEquals("sessionUUID=new-login", discard.getHeader("Cookie"))
         assertEquals("sessionUUID=current-session", server.takeRequest().getHeader("Cookie"))
      }
   }

   @Test fun missingFeaturePreventsSendingTheAuthorizationCode() = runBlocking {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(VERSION_FIXTURE))
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin)
         repository.setEnvironment(Environment("fixture", origin))
         assertTrue(runCatching { repository.exchangeNativeSession(request) }.exceptionOrNull() is UiApiCompatibilityException)
         assertEquals(1, server.requestCount)
         val discovery = server.takeRequest()
         assertEquals("/qqq/v1/version", discovery.path)
         assertEquals(0L, discovery.bodySize)
      }
   }

   @Test fun invalidOriginalNoncePreventsAnyNetworkRequest() = runBlocking {
      MockWebServer().use { server ->
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin)
         repository.setEnvironment(Environment("fixture", origin))
         for(value in listOf(null, "", "n".repeat(15), "n".repeat(513), "not valid nonce", "n".repeat(20) + "="))
            assertTrue(runCatching { repository.exchangeNativeSession(request.copy(expectedNonce = value)) }.exceptionOrNull() is IllegalArgumentException)
         assertEquals(0, server.requestCount)
      }
   }

   @Test fun rejectedNonceNeverRetriesAnUnprotectedExchange() = runBlocking {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(version))
         server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"AUTHENTICATION_FAILED","message":"Sign-in failed"}"""))
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin)
         repository.setEnvironment(Environment("fixture", origin))
         assertTrue(runCatching { repository.exchangeNativeSession(request) }.isFailure)
         assertEquals(2, server.requestCount)
         server.takeRequest()
         assertTrue(server.takeRequest().body.readUtf8().contains("expectedNonce"))
         assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
      }
   }
}
