/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.controllers

import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

internal const val VERSION_FIXTURE = """{"apiVersion":"1.8.0","features":["version","metaData","processes","processCancel","extensions","widgets"],"clientChannels":["web","api","android"],"extensions":[{"name":"mobile-scanners","version":"1.0.0","pathPrefix":"/qbit/mobile-scanners"}]}"""

class UiApiCompatibilityTest
{
   @Test fun incompatibleDiscoveryNeverSendsCredentialsOrMetadata() = runBlocking()
   {
      val incompatible = listOf(
         VERSION_FIXTURE.replace("1.8.0", "2.0.0"),
         VERSION_FIXTURE.replace("1.8.0", "1.7.9"),
         VERSION_FIXTURE.replace("1.8.0", "1.8.0-preview"),
         VERSION_FIXTURE.replace("\"widgets\"", "\"other\""),
         VERSION_FIXTURE.replace("mobile-scanners", "other"),
         VERSION_FIXTURE.replace("1.0.0", "2.0.0"),
         VERSION_FIXTURE.replace("/qbit/mobile-scanners", "/evil"),
         VERSION_FIXTURE.replace("\"android\"", "\"future-client\""),
      )
      for(payload in incompatible) MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(payload))
         val origin = server.url("/").toString()
         val gate = UiApiCompatibility(origin, true)
         val repository = NetworkQQQRepository(origin, compatibility = gate) { mapOf("X-QQQ-Device-Token" to "private-token") }
         repository.setEnvironment(Environment("test", origin))
         repository.setSessionUUID("private-session")
         val result = runCatching { repository.loginWithPassword("private-user", "private-password") }
         assertTrue(result.exceptionOrNull() is UiApiCompatibilityException)
         val request = server.takeRequest()
         assertEquals("/qqq/v1/version", request.path)
         assertEquals(ANDROID_CLIENT, request.getHeader("X-QQQ-Client"))
         listOf("Cookie", "Authorization", "X-QQQ-Device-Token").forEach { assertNull(request.getHeader(it)) }
         assertEquals(0L, request.bodySize)
         assertEquals(1, server.requestCount)
      }
   }

   @Test fun acceptsAdditiveVersionFeaturesAndUnknownMetadataEnums()
   {
      val json = Json { ignoreUnknownKeys = true }
      val version = json.decodeFromString<UiApiVersion>(VERSION_FIXTURE.replace("1.8.0", "1.99.9").replace("1.0.0", "1.12.1"))
      UiApiCompatibility.validate(version.copy(features = version.features + "futureFeature"), true)
      assertEquals(QComponentType.UNKNOWN, json.decodeFromString<QFrontendComponent>("""{"type":"FUTURE_COMPONENT","future":true}""").type)
      assertEquals(QFieldType.UNKNOWN, json.decodeFromString<QFieldMetaData>("""{"name":"x","label":"Future field","type":"FUTURE_FIELD"}""").type)
      assertEquals(QAppNodeType.UNKNOWN, json.decodeFromString<QAppNodeType>("\"FUTURE_NODE\""))
      val auth = json.decodeFromString<com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData>("""{"type":"FUTURE_PROVIDER","values":{"future":true}}""")
      assertEquals("FUTURE_PROVIDER", auth.type)
      assertEquals(AdornmentType.UNKNOWN, json.decodeFromString<AdornmentType>("\"FUTURE_ADORNMENT\""))
   }

   @Test fun discoveryIsCachedOnlyForItsOriginAndCarriesNoCookies()
   {
      MockWebServer().use { first -> MockWebServer().use { second ->
         first.enqueue(MockResponse().setBody(VERSION_FIXTURE).addHeader("Set-Cookie", "sessionUUID=untrusted; Path=/"))
         second.enqueue(MockResponse().setBody(VERSION_FIXTURE))
         val gate = UiApiCompatibility(first.url("/").toString(), true)
         gate.verify(); gate.verify()
         UiApiCompatibility(second.url("/").toString(), true).verify()
         assertEquals(1, first.requestCount)
         assertNull(second.takeRequest().getHeader("Cookie"))
      } }
   }

   @Test fun nullExtensionsAreAnEmptyAdvertisementRatherThanMalformedDiscovery()
   {
      val payload = VERSION_FIXTURE.replace("\"extensions\":[{\"name\":\"mobile-scanners\",\"version\":\"1.0.0\",\"pathPrefix\":\"/qbit/mobile-scanners\"}]", "\"extensions\":null")
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(payload))
         assertTrue(UiApiCompatibility(server.url("/").toString()).verify().extensions.isEmpty())
      }
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(payload))
         val error = runCatching { UiApiCompatibility(server.url("/").toString(), true).verify() }.exceptionOrNull()
         assertTrue(error is UiApiCompatibilityException)
         assertTrue(error?.message.orEmpty().startsWith("This server is missing the mobile-scanners extension."))
         assertEquals(1, server.requestCount)
         assertNull(server.takeRequest().getHeader("Authorization"))
      }
   }

   @Test fun cancellationUsesPublishedOperationAndSemanticErrorsRemainStructured() = runBlocking()
   {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(VERSION_FIXTURE))
         server.enqueue(MockResponse().setBody("{}"))
         server.enqueue(MockResponse().setResponseCode(400).setBody("""{"message":"Fix the reference","code":"FUTURE_VALIDATION","fieldErrors":[{"field":"reference","message":"Required","code":"REQUIRED"}],"traceId":"trace-123","future":true}"""))
         val origin = server.url("/").toString()
         val outcomes = mutableListOf<RequestOutcome>()
         val repository = NetworkQQQRepository(origin, requestObserver = { outcomes.add(it) })
         repository.setEnvironment(Environment("test", origin))
         repository.setSessionUUID("synthetic-session")
         repository.processCancel("capture", "synthetic-process")
         assertEquals("/qqq/v1/version", server.takeRequest().path)
         val cancellation = server.takeRequest()
         assertEquals("POST", cancellation.method)
         assertEquals("/qqq/v1/processes/capture/synthetic-process/cancel", cancellation.path)
         assertEquals(ANDROID_CLIENT, cancellation.getHeader("X-QQQ-Client"))
         val error = runCatching { repository.getMetaData("test", "1", "test", "1") }.exceptionOrNull() as UiApiException
         assertEquals(400, error.httpStatus)
         assertEquals("FUTURE_VALIDATION", error.code)
         assertEquals("Fix the reference", error.message)
         assertEquals(UiApiFieldError("reference", "Required", "REQUIRED"), error.fieldErrors.single())
         assertEquals("trace-123", error.traceId)
         assertFalse(outcomes.toString().contains("reference"))
         assertFalse(outcomes.toString().contains("trace-123"))
      }
   }

   @Test fun metadataMediaReferencesResolveWithoutInventingApiRoutes()
   {
      val repository = NetworkQQQRepository("https://server.example/")
      assertEquals("https://cdn.example/logo.png", repository.getURI("https://cdn.example/logo.png"))
      assertEquals("https://server.example/qqq/v1/avatars/test", repository.getURI("/qqq/v1/avatars/test"))
      assertTrue(runCatching { repository.getURI("file:///private") }.isFailure)
      assertTrue(runCatching { repository.getURI("https://user:secret@cdn.example/logo") }.isFailure)
   }
}
