/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.processes

/*******************************************************************************
 ** One sound player per composed process screen, released on stop or disposal and recreated on demand.
 *******************************************************************************/
internal class ProcessSound(private val create: () -> Playback?) {
   /*******************************************************************************
    ** Small platform boundary so lifecycle resource ownership can be verified without native audio.
    *******************************************************************************/
   interface Playback {
      /*******************************************************************************
       ** Begin optional process audio through an injected resource boundary so playback behavior is testable.
       *******************************************************************************/
      fun start()

      /*******************************************************************************
       ** Release optional audio ownership when playback finishes or its process lifetime ends.
       *******************************************************************************/
      fun release()
   }

   private var player: Playback? = null

   /*******************************************************************************
    ** Repeated submits/ticks reuse the same live player.
    *******************************************************************************/
   fun play() {
      try {
         if (player == null) player = create()
         player?.start()
      } catch (exception: RuntimeException) {
         // Optional audio feedback must not interrupt a process action when the device's audio service fails.
         release()
      }
   }

   /*******************************************************************************
    ** Stop and disposal may both fire; release each allocated player exactly once.
    *******************************************************************************/
   fun release() {
      val previous = player
      player = null
      try {
         previous?.release()
      } catch (exception: RuntimeException) { /* The audio service may already have reclaimed the resource. */
      }
   }
}
