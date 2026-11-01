/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/*******************************************************************************
 ** Presentation accepts only the fixed, credential-free event model, never raw HTTP text.
 *******************************************************************************/
internal fun DiagnosticEvent.isProblem(): Boolean = httpStatus?.let { it !in 200..299 } == true || code in setOf(
   DiagnosticCode.COMPATIBILITY_FAILED, DiagnosticCode.BRAND_DEFAULT, DiagnosticCode.LOGO_UNAVAILABLE,
   DiagnosticCode.CONNECTION_FAILED, DiagnosticCode.NATIVE_TRANSPORT_FAILED,
   DiagnosticCode.DEVICE_TRANSPORT_FAILED, DiagnosticCode.SESSION_TRANSPORT_FAILED
)

/*******************************************************************************
 ** Translate fixed event categories into operator language without including raw server text.
 *******************************************************************************/
internal fun DiagnosticEvent.title(): String = when (code) {
   DiagnosticCode.COMPATIBILITY_RESPONSE -> "Server compatibility checked"
   DiagnosticCode.COMPATIBILITY_FAILED -> "Server compatibility check failed"
   DiagnosticCode.BRAND_LOADED -> "Server appearance loaded"
   DiagnosticCode.BRAND_DEFAULT -> "Default appearance used"
   DiagnosticCode.LOGO_LOADED -> "Server logo loaded"
   DiagnosticCode.LOGO_UNAVAILABLE -> "Server logo unavailable"
   DiagnosticCode.CONNECTION_OK -> "Server reachable"
   DiagnosticCode.CONNECTION_FAILED -> "Connection check failed"
   DiagnosticCode.NATIVE_RESPONSE -> if (isProblem()) "App request failed" else "App request completed"
   DiagnosticCode.NATIVE_TRANSPORT_FAILED -> "App request could not connect"
   DiagnosticCode.DEVICE_RESPONSE -> if (isProblem()) "Device request failed" else "Device request completed"
   DiagnosticCode.DEVICE_TRANSPORT_FAILED -> "Device request could not connect"
   DiagnosticCode.SESSION_RESPONSE -> if (isProblem()) "Session request failed" else "Session request completed"
   DiagnosticCode.SESSION_TRANSPORT_FAILED -> "Session request could not connect"
}

/*******************************************************************************
 ** Explain status codes and duration using the credential-free event model alone.
 *******************************************************************************/
internal fun DiagnosticEvent.detail(): String {
   val result = when (httpStatus) {
      null -> "No HTTP response"
      401 -> "HTTP 401 · Sign-in required or expired"
      403 -> "HTTP 403 · Request not permitted"
      in 500..599 -> "HTTP $httpStatus · Server error"
      else -> "HTTP $httpStatus"
   }
   val validation = if (code == DiagnosticCode.CONNECTION_FAILED && httpStatus in 200..299) " · Unexpected status response" else ""
   return "$result$validation · ${elapsedMillis} ms"
}

/*******************************************************************************
 ** Render the event instant in the selected zone without changing its stored ordering.
 *******************************************************************************/
internal fun DiagnosticEvent.localTime(zone: ZoneId = ZoneId.systemDefault()): String =
   DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withZone(zone).format(time)

/*******************************************************************************
 ** Present newest activity first while retaining only actionable categories when filtering problems.
 *******************************************************************************/
internal fun diagnosticRows(events: List<DiagnosticEvent>, problemsOnly: Boolean): List<DiagnosticEvent> =
   events.asReversed().filter { !problemsOnly || it.isProblem() }

/*******************************************************************************
 ** Select explicit reachability checks rather than inferring connectivity from authentication requests.
 *******************************************************************************/
internal fun latestConnectionCheck(events: List<DiagnosticEvent>): DiagnosticEvent? = events.lastOrNull {
   it.code == DiagnosticCode.CONNECTION_OK || it.code == DiagnosticCode.CONNECTION_FAILED
}

/*******************************************************************************
 ** Build a shareable report solely from bounded, credential-free diagnostic records.
 *******************************************************************************/
internal fun diagnosticReport(events: List<DiagnosticEvent>, problemsOnly: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
   val rows = diagnosticRows(events, problemsOnly)
   val date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(zone)
   return buildString {
      appendLine("QQQ Mobile connection diagnostics")
      appendLine("Connection checks measure server reachability only; sign-in and device approval are separate.")
      appendLine(if (problemsOnly) "Problems · newest first" else "All events · newest first")
      if (rows.isEmpty()) append("No events.")
      rows.forEach { appendLine("${date.format(it.time)} · ${it.title()} · ${it.detail()}") }
   }.trimEnd()
}
