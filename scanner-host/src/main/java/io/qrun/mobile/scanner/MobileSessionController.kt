/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One authenticated session. No screenshots or telemetry survive this controller's lifetime. */
internal class MobileSessionController(
   private val session: String,
   private val api: MobileSessionTransport,
   private val readScreen: () -> SessionScreen,
   private val capture: suspend (ScreenLease) -> ScreenFrame?,
   private val onUnauthorized: () -> Unit,
   private val now: () -> Long = android.os.SystemClock::elapsedRealtime,
)
{
   var operatorPaused by mutableStateOf(false)
      private set
   var viewing by mutableStateOf(false)
      private set
   private var ended = false
   private var generation = 0L
   private var running: Job? = null
   private var lease: ScreenLease? = null
   private var leaseExpiresAt = 0L
   private var sequence = 0L

   private fun screen() = readScreen().copy(paused = operatorPaused)

   fun pauseViewing() { operatorPaused = true; generation++; viewing = false }
   fun allowViewing() { operatorPaused = false; generation++ }

   /** Invalidating synchronously prevents a completed PixelCopy callback from uploading after exit. */
   fun invalidate() { ended = true; generation++; lease = null; viewing = false; running?.cancel() }

   suspend fun end(reason: String)
   {
      if(ended) return
      ended = true
      generation++
      lease = null
      viewing = false
      val job = running
      if(job != currentCoroutineContext()[Job]) job?.cancelAndJoin()
      if(session.isNotBlank()) try { api.end(session, reason) }
      catch(exception: CancellationException) { throw exception }
      catch(exception: Exception) { /* Standard logout still runs and always clears this device. */ }
   }

   suspend fun run() = coroutineScope()
   {
      if(session.isBlank() || ended) return@coroutineScope
      running = currentCoroutineContext()[Job]
      val changed = Channel<Unit>(Channel.CONFLATED)
      val observer = launch { snapshotFlow { screen() }.collect { changed.trySend(Unit) } }
      var previous: SessionScreen? = null
      var nextHeartbeat = 0L
      var nextFrame = 0L
      var heartbeatInterval = 5000L
      try
      {
         while(isActive && !ended)
         {
            val state = screen()
            if(state != previous)
            {
               generation++
               previous = state
               nextHeartbeat = 0L
               if(!state.available) { lease = null; viewing = false }
            }
            if(!state.authenticated || !state.approved) break
            if(now() >= nextHeartbeat)
            {
               val started = now()
               try
               {
                  val response = api.heartbeat(session, state)
                  heartbeatInterval = response.intervalMillis
                  // A slow response cannot extend the lease beyond its server-derived remaining lifetime.
                  val newLease = response.lease
                  leaseExpiresAt = started + (newLease?.durationMillis ?: 0)
                  if(newLease?.id != lease?.id) sequence = newLease?.nextSequence ?: 0L
                  else sequence = maxOf(sequence, newLease?.nextSequence ?: 0L)
                  lease = newLease
               }
               catch(exception: CancellationException) { throw exception }
               catch(exception: Exception)
               {
                  lease = null
                  if(exception is UiApiException && exception.httpStatus in setOf(401, 403)) { onUnauthorized(); break }
               }
               nextHeartbeat = if(state.foreground) now() + heartbeatInterval else Long.MAX_VALUE
            }
            val activeLease = lease
            if(screen() != state) continue
            viewing = activeLease != null && state.available && now() < leaseExpiresAt
            if(viewing && activeLease != null && now() >= nextFrame)
            {
               val ticket = ScreenTicket(generation, state, activeLease.id)
               val frame = try { capture(activeLease) }
               catch(exception: CancellationException) { throw exception }
               catch(exception: Exception) { null }
               if(frame != null && ticket.valid(generation, screen(), lease?.id, now(), leaseExpiresAt) && !ended)
               {
                  try
                  {
                     // This check is deliberately adjacent to the cancellable upload, after JPEG encoding.
                     if(ticket.valid(generation, screen(), lease?.id, now(), leaseExpiresAt) && !ended)
                        api.screen(session, activeLease.id, sequence++, frame)
                  }
                  catch(exception: CancellationException) { throw exception }
                  catch(exception: Exception)
                  {
                     lease = null
                     viewing = false
                     nextHeartbeat = now() + heartbeatInterval
                     if(exception is UiApiException && exception.httpStatus == 401) { onUnauthorized(); break }
                  }
               }
               nextFrame = now() + activeLease.intervalMillis
            }
            val deadline = if(viewing) minOf(nextHeartbeat, nextFrame, leaseExpiresAt) else nextHeartbeat
            withTimeoutOrNull((deadline - now()).coerceIn(1, 15000)) { changed.receive() }
         }
      }
      finally { observer.cancel(); changed.close(); viewing = false; lease = null; running = null }
   }
}
