/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import java.time.Instant
import org.json.JSONObject

/*******************************************************************************
 ** Distinguish application-only pixels from separately consented device projection in every lease and frame.
 *******************************************************************************/
internal enum class CaptureMode { APP_CONTENT, DEVICE_SCREEN }

/*******************************************************************************
 ** Metadata only. Authentication credentials and field/scan values never enter telemetry.
 *******************************************************************************/
internal data class SessionScreen(
   val foreground: Boolean,
   val authenticated: Boolean,
   val focused: Boolean,
   val approved: Boolean,
   val hasPassword: Boolean,
   val ready: Boolean,
   val paused: Boolean,
   val navigationRevision: Long,
   val stepRevision: Int,
   val appName: String? = null,
   val processName: String? = null,
   val processInstanceId: String? = null,
   val stepName: String? = null,
   val label: String? = null,
   val location: LocationReport = LocationReport(),
   val captureMode: CaptureMode = CaptureMode.APP_CONTENT,
) {
   val blockedReason: String?
      get() = when {
         !authenticated -> "SIGNED_OUT"
         !approved -> "DEVICE_NOT_APPROVED"
         !foreground && captureMode == CaptureMode.APP_CONTENT -> "APP_BACKGROUND"
         !focused && captureMode == CaptureMode.APP_CONTENT -> "APP_NOT_FOCUSED"
         paused -> "OPERATOR_PAUSED"
         hasPassword -> "PASSWORD_SCREEN"
         !ready -> "SCREEN_LOADING"
         else -> null
      }
   val available: Boolean get() = blockedReason == null
}

/*******************************************************************************
 ** Carry one bounded encoded capture with its dimensions, timestamp and consent mode.
 *******************************************************************************/
internal data class ScreenFrame(
   val jpeg: ByteArray, val width: Int, val height: Int, val capturedAt: String,
   val captureMode: CaptureMode = CaptureMode.APP_CONTENT
)

/*******************************************************************************
 ** Carry server-issued capture limits and sequence continuity for the current authenticated viewer.
 *******************************************************************************/
internal data class ScreenLease(
   val id: String, val durationMillis: Long, val intervalMillis: Long,
   val maxWidth: Int, val maxHeight: Int, val maxBytes: Int, val nextSequence: Long,
   val captureMode: CaptureMode = CaptureMode.APP_CONTENT
)

/*******************************************************************************
 ** Represent the authoritative session and optional bounded viewing lease returned by a heartbeat.
 *******************************************************************************/
internal data class SessionHeartbeat(
   val sessionId: String, val intervalMillis: Long, val lease: ScreenLease?,
   val supportedCaptureModes: Set<CaptureMode> = setOf(CaptureMode.APP_CONTENT)
) {
   /*******************************************************************************
    ** Parse server heartbeat limits at the boundary before they govern capture scheduling.
    *******************************************************************************/
   companion object {
      /*******************************************************************************
       ** Require an active server session and clamp advertised timing and size limits before any capture.
       *******************************************************************************/
      fun parse(payload: String): SessionHeartbeat {
         val value = JSONObject(payload)
         require(value.getString("status") == "ACTIVE") { "Inactive mobile session." }
         val serverTime = Instant.parse(value.getString("serverTime"))
         val lease = value.optJSONObject("screenMirror")?.let {
            ScreenLease(
               it.getString("leaseId"),
               (Instant.parse(it.getString("expiresAt")).toEpochMilli() - serverTime.toEpochMilli()).coerceIn(0, 30000),
               it.getLong("frameIntervalMillis").coerceIn(1000, 30000),
               it.getInt("maxWidth").coerceIn(1, 640), it.getInt("maxHeight").coerceIn(1, 1280),
               it.getInt("maxBytes").coerceIn(1, 262144), it.getLong("nextSequence").coerceAtLeast(0),
               CaptureMode.valueOf(it.optString("captureMode", "APP_CONTENT"))
            )
         }
         val advertised = value.optJSONArray("supportedCaptureModes")
         val modes = if (advertised == null) setOf(CaptureMode.APP_CONTENT) else
            (0 until advertised.length()).mapNotNull { index -> CaptureMode.entries.find { it.name == advertised.optString(index) } }.toSet()
         return SessionHeartbeat(value.getString("sessionId"), value.getLong("heartbeatIntervalMillis").coerceIn(1000, 15000), lease, modes)
      }
   }
}

/*******************************************************************************
 ** A frame is usable only for the exact state and session generation in which capture began.
 *******************************************************************************/
internal data class ScreenTicket(val generation: Long, val state: SessionScreen, val leaseId: String) {
   /*******************************************************************************
    ** Reject completed capture when host state, generation, lease identity or monotonic expiry has changed.
    *******************************************************************************/
   fun valid(generation: Long, state: SessionScreen, leaseId: String?, now: Long, expiresAt: Long) =
      this.generation == generation && this.state == state && state.available && this.leaseId == leaseId && now < expiresAt
}
