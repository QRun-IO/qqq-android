/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.processes

import org.junit.Assert.assertEquals
import org.junit.Test

/*******************************************************************************
 ** Verify repeated sound playback and background/disposal do not leak or reuse a released player.
 *******************************************************************************/
class ProcessSoundTest {
   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun unavailableAudioDoesNotFailProcessActionsAndCanRecover() {
      var attempts = 0
      var released = 0
      var played = 0
      val sound = ProcessSound {
         val attempt = ++attempts
         if (attempt == 1) throw IllegalStateException("Audio service unavailable")
         object : ProcessSound.Playback {
            /*******************************************************************************
             **
             *******************************************************************************/
            override fun start() {
               if (attempt == 2) throw IllegalStateException("Audio device disconnected")
               played++
            }

            /*******************************************************************************
             **
             *******************************************************************************/
            override fun release() {
               released++
               if (attempt == 2) throw IllegalStateException("Already reclaimed")
            }
         }
      }
      repeat(3) { sound.play() }
      sound.release()
      sound.release()
      assertEquals(3, attempts)
      assertEquals(1, played)
      assertEquals(2, released)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun repeatedPlaybackReusesAndLifecycleReleaseIsIdempotent() {
      val events = mutableListOf<String>()
      var created = 0
      val sound = ProcessSound {
         val id = ++created
         events.add("create$id")
         object : ProcessSound.Playback {
            /*******************************************************************************
             **
             *******************************************************************************/
            override fun start() {
               events.add("play$id")
            }

            /*******************************************************************************
             **
             *******************************************************************************/
            override fun release() {
               events.add("release$id")
            }
         }
      }
      repeat(3) { sound.play() }
      sound.release()
      sound.release()
      sound.play()
      sound.release()
      assertEquals(listOf("create1", "play1", "play1", "play1", "release1", "create2", "play2", "release2"), events)
   }
}
