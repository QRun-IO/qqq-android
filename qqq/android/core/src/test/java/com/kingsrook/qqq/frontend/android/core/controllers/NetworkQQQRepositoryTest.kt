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

/*******************************************************************************
 ** Modern servers return HttpOnly session cookies; both credentials and redirects remain origin-scoped.
 *******************************************************************************/
class NetworkQQQRepositoryTest {
   @Test
   fun searchAndCountKeepIdenticalPredicatesAndOpaqueParentKeys() = runBlocking() {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(VERSION_FIXTURE))
         server.enqueue(MockResponse().setBody("{\"count\":1}"))
         server.enqueue(MockResponse().setBody("{\"records\":[]}"))
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin)
         repository.setEnvironment(Environment("test", origin))
         val search = com.kingsrook.qqq.frontend.android.core.model.RecordSearch("a\"b", listOf("reference", "name"), mapOf("parent" to kotlinx.serialization.json.JsonPrimitive("0007")))
         assertEquals(1L, repository.countRecords("items", search))
         repository.searchRecords("items", "id", search, 25)
         server.takeRequest()
         val counted = server.takeRequest()
         assertEquals("/qqq/v1/table/items/count", counted.path)
         val expected = org.json.JSONObject(counted.body.readUtf8()).getJSONObject("filter")
         val query = org.json.JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("filter")
         assertEquals(25, query.remove("skip"))
         assertEquals(25, query.remove("limit"))
         query.remove("orderBys")
         assertTrue(expected.similar(query))
         assertEquals("0007", expected.getJSONArray("criteria").getJSONObject(0).getJSONArray("values").getString(0))
         assertEquals("OR", expected.getJSONArray("subFilters").getJSONObject(0).getString("booleanOperator"))
      }
   }

   @Test
   fun relatedQueriesAreBoundedAndCannotDropTheParentFilter() = runBlocking() {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(VERSION_FIXTURE))
         server.enqueue(MockResponse().setBody("""{"records":[]}"""))
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin)
         repository.setEnvironment(Environment("test", origin))
         repository.queryRelatedRecords("lines", "id", mapOf("parent" to kotlinx.serialization.json.JsonPrimitive("0007")), 10, 10)
         server.takeRequest()
         val request = server.takeRequest()
         assertEquals("/qqq/v1/table/lines/query", request.path)
         val filter = org.json.JSONObject(request.body.readUtf8()).getJSONObject("filter")
         assertEquals(10, filter.getInt("skip"))
         assertEquals(10, filter.getInt("limit"))
         assertEquals("AND", filter.getString("booleanOperator"))
         val criteria = filter.getJSONArray("criteria").getJSONObject(0)
         assertEquals("parent", criteria.getString("fieldName"))
         assertEquals("EQUALS", criteria.getString("operator"))
         assertEquals("0007", criteria.getJSONArray("values").getString(0))
         assertTrue(runCatching { repository.queryRelatedRecords("lines", "id", emptyMap(), 0) }.isFailure)
         assertTrue(runCatching { repository.queryRelatedRecords("lines", "id", mapOf("parent" to kotlinx.serialization.json.JsonNull), 0) }.isFailure)
      }
   }

   /*******************************************************************************
    ** Native browsing retains server identity, opaque ids, bounded paging and read-only routes.
    *******************************************************************************/
   @Test
   fun recordBrowsingUsesSemanticRoutesAndPreservesIds() = runBlocking() {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(VERSION_FIXTURE))
         server.enqueue(MockResponse().setBody("""{"name":"items","label":"Items","primaryKeyField":"key","fields":{"key":{"name":"key","label":"Key","type":"STRING"}}}"""))
         server.enqueue(MockResponse().setBody("""{"records":[{"tableName":"items","recordLabel":"Item","values":{"key":"0007","quantity":0},"displayValues":{"quantity":"Zero"}}]}"""))
         server.enqueue(MockResponse().setBody("""{"record":{"tableName":"items","values":{"key":"0007"}}}"""))
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin, additionalHeaders = { mapOf("X-QQQ-Device-Id" to "fixture-device") })
         repository.setEnvironment(Environment("test", origin))
         repository.setSessionUUID("fixture-session")
         assertEquals("key", repository.getTableMetaData("items").primaryKeyField)
         val records = repository.queryRecords("items", "key", 25, 25)
         assertEquals("0007", records.single().id("key"))
         assertEquals("Zero", records.single().display("quantity"))
         assertEquals("0007", repository.getRecord("items", "0007").id("key"))
         assertEquals("/qqq/v1/version", server.takeRequest().path)
         assertEquals("/qqq/v1/metaData/table/items", server.takeRequest().path)
         val query = server.takeRequest()
         assertEquals("/qqq/v1/table/items/query", query.path)
         assertEquals("fixture-device", query.getHeader("X-QQQ-Device-Id"))
         assertEquals("sessionUUID=fixture-session", query.getHeader("Cookie"))
         val filter = org.json.JSONObject(query.body.readUtf8()).getJSONObject("filter")
         assertEquals(25, filter.getInt("skip"))
         assertEquals(25, filter.getInt("limit"))
         assertEquals("key", filter.getJSONArray("orderBys").getJSONObject(0).getString("fieldName"))
         val get = server.takeRequest()
         assertEquals("GET", get.method)
         assertEquals("/qqq/v1/table/items/0007", get.path)
         assertTrue(runCatching { repository.queryRecords("items", "key", 0, 1000) }.isFailure)
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
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

   /*******************************************************************************
    ** Published `searchProcessPossibleValues`: JSON body with the parent values and FORM use case; the response is
    ** `{options:[{id,label}]}` and the server omits `options` entirely when nothing matches.
    *******************************************************************************/
   @Test
   fun processPossibleValuesUsesThePublishedJsonOperation() = runBlocking()
   {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(VERSION_FIXTURE))
         server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"options":[{"id":"4x6","label":"4 x 6 in"},{"id":7,"label":"Seven"}]}"""))
         server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin)
         repository.setEnvironment(Environment("test", origin))
         val options = repository.processPossibleValues("echoPrint", "mediaId", mapOf("printerId" to "printer"), mapOf("echoRunId" to "run"))
         assertEquals("/qqq/v1/version", server.takeRequest().path)
         val request = server.takeRequest()
         assertEquals("POST", request.method)
         assertEquals("/qqq/v1/processes/echoPrint/possibleValues/mediaId", request.path)
         assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
         val body = org.json.JSONObject(request.body.readUtf8())
         assertEquals("printer", body.getJSONObject("values").getString("printerId"))
         assertEquals("run", body.getJSONObject("processValues").getString("echoRunId"))
         assertEquals("form", body.getString("useCase"))
         assertEquals(listOf("4x6", "7"), options.map { it.id.toString() })
         assertTrue("A JSON integer id must stay an integer for INTEGER fields", options[1].id is Int)
         assertEquals(listOf("4 x 6 in", "Seven"), options.map { it.label })
         assertTrue(repository.processPossibleValues("echoPrint", "mediaId", emptyMap(), emptyMap()).isEmpty())
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
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

   /*******************************************************************************
    **
    *******************************************************************************/
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

   /*******************************************************************************
    **
    *******************************************************************************/
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

   /*******************************************************************************
    ** Cookie-bearing native mutations (session/process POSTs) carry the exact request origin, and a caller-supplied
    ** additional header can neither add a different Origin nor remove the real one. Login (no cookie yet) carries it too.
    *******************************************************************************/
   @Test
   fun cookieBearingPostsCarryTheExactRequestOriginEvenIfAdditionalHeadersTryToSetOne() = runBlocking()
   {
      MockWebServer().use { server ->
         server.start()
         server.enqueue(MockResponse().setBody(VERSION_FIXTURE))
         server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"type":"COMPLETE","processUUID":"process","nextStep":"result","values":{}}"""))
         server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
         val origin = server.url("/").toString()
         val repository = NetworkQQQRepository(origin, additionalHeaders = { mapOf("Origin" to "https://evil.example", "X-QQQ-Device-Id" to "test-device") })
         repository.setEnvironment(Environment("test", origin))
         repository.setSessionUUID("synthetic-session")
         repository.processStep("edit", "process", "input", mapOf("a" to "b"), 1000)
         repository.logout()
         server.takeRequest()
         // Forwarded debug servers may listen elsewhere (for example 8080): only the URL the client used matters.
         val expected = "http://${server.hostName}:${server.port}"
         listOf(server.takeRequest(), server.takeRequest()).forEach { request ->
            assertEquals("POST", request.method)
            assertEquals("sessionUUID=synthetic-session", request.getHeader("Cookie"))
            assertEquals(expected, request.getHeader("Origin"))
            assertEquals(1, request.headers.values("Origin").size)
            assertEquals("http://" + request.getHeader("Host"), request.getHeader("Origin"))
            assertEquals("test-device", request.getHeader("X-QQQ-Device-Id"))
         }
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun capturesHttpOnlySessionAndDoesNotSendItToAnotherServer() = runBlocking()
   {
      MockWebServer().use { first ->
         MockWebServer().use { second ->
            first.start()
            second.start()
            first.enqueue(MockResponse().setBody(VERSION_FIXTURE))
            first.enqueue(
               MockResponse().setHeader("Content-Type", "application/json")
                  .addHeader("Set-Cookie", "sessionUUID=synthetic-session; HttpOnly; Path=/").setBody("{\"values\":{}}")
            )
            first.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
            second.enqueue(MockResponse().setBody(VERSION_FIXTURE))
            second.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
            val origin = first.url("/").toString()
            val repository = NetworkQQQRepository(origin) { server ->
               if (server == origin) mapOf("X-QQQ-Device-Id" to "test-device") else emptyMap()
            }
            repository.setEnvironment(Environment("first", origin))
            val login = repository.loginWithPassword("demo", "synthetic-password")
            assertEquals("synthetic-session", login.uuid)
            repository.setSessionUUID(requireNotNull(login.uuid))
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
