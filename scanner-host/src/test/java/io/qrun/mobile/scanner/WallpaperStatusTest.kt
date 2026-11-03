/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*******************************************************************************
 ** The wallpaper may show identity and IP only on the unlocked home screen, only while fresh, and never keeps either
 ** after the session ends.
 *******************************************************************************/
class WallpaperStatusTest {
   private var elapsed = 1_000L
   private var wall = 1_700_000_000_000L
   private val store = WallpaperStatusStore({ elapsed }, { wall })
   private val ip = "IP: wlan0 10.0.0.5"

   private fun render(context: WallpaperContext, now: Long = elapsed) =
      WallpaperPresentation.render(store.snapshot, context, ip, now) { "HH:mm" }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun newProcessIsUnknownAndShowsNoIdentity() {
      assertEquals(WallpaperSessionState.UNKNOWN, store.snapshot.state)
      assertEquals(listOf("Session: not available", ip), render(WallpaperContext.HOME).lines)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun previewAndLockedShowBrandOnlyEvenWhenSignedIn() {
      store.signedIn("Ada Lovelace", "qqq.example.com", foreground = true)
      listOf(WallpaperContext.PREVIEW, WallpaperContext.LOCKED).forEach {
         val frame = render(it)
         assertEquals(HostBrand().displayName, frame.header)
         assertTrue(frame.lines.isEmpty())
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun homeShowsNameServerAndIpWhileForeground() {
      store.signedIn("Ada Lovelace", "qqq.example.com", foreground = true)
      assertEquals(listOf("Signed in: Ada Lovelace", "Server: qqq.example.com", ip), render(WallpaperContext.HOME).lines)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun backgroundSessionIsLabelledLastConfirmedNotLive() {
      store.signedIn("Ada Lovelace", "qqq.example.com", foreground = true)
      store.signedIn("Ada Lovelace", "qqq.example.com", foreground = false)
      val lines = render(WallpaperContext.HOME, elapsed + 60_000).lines
      assertTrue(lines.contains("Signed in: Ada Lovelace"))
      assertTrue(lines.contains("Last confirmed HH:mm (app in background)"))
      assertFalse(lines.any { it.contains("verified", ignoreCase = true) && !it.contains("unverified", ignoreCase = true) })
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun nameIsWithdrawnAfterTheStaleWindow() {
      store.signedIn("Ada Lovelace", "qqq.example.com", foreground = false)
      val justBefore = elapsed + WallpaperPresentation.STALE_AFTER_MILLIS - 1
      val atWindow = elapsed + WallpaperPresentation.STALE_AFTER_MILLIS
      assertTrue(render(WallpaperContext.HOME, justBefore).lines.contains("Signed in: Ada Lovelace"))
      assertEquals(1L, WallpaperPresentation.millisUntilStale(store.snapshot, justBefore))
      val stale = render(WallpaperContext.HOME, atWindow).lines
      assertEquals(listOf("Session unverified", "Last confirmed HH:mm", ip), stale)
      assertNull(WallpaperPresentation.millisUntilStale(store.snapshot, atWindow))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun foregroundSessionNeverGoesStaleOrNeedsATimer() {
      store.signedIn("Ada Lovelace", null, foreground = true)
      assertFalse(WallpaperPresentation.isStale(store.snapshot, elapsed + 10 * WallpaperPresentation.STALE_AFTER_MILLIS))
      assertNull(WallpaperPresentation.millisUntilStale(store.snapshot, elapsed))
      assertEquals(listOf("Signed in: Ada Lovelace", ip), render(WallpaperContext.HOME).lines)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun signOutDropsNameAndServerButClearReturnsToUnknown() {
      store.signedIn("Ada Lovelace", "qqq.example.com", foreground = true)
      store.signedOutIfSignedIn()
      assertEquals(WallpaperSessionState.SIGNED_OUT, store.snapshot.state)
      assertNull(store.snapshot.displayName)
      assertNull(store.snapshot.serverHost)
      assertEquals(listOf("Signed out", ip), render(WallpaperContext.HOME).lines)
      store.signedIn("Grace Hopper", "qqq.example.com", foreground = true)
      store.clear()
      assertEquals(WallpaperSessionState.UNKNOWN, store.snapshot.state)
      assertNull(store.snapshot.displayName)
      assertFalse(render(WallpaperContext.HOME).lines.any { it.contains("Grace") })
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun startupDoesNotClaimSignedOutBeforeAnySession() {
      store.signedOutIfSignedIn()
      assertEquals(WallpaperSessionState.UNKNOWN, store.snapshot.state)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun displayTextIsSingleLineAndBounded() {
      store.signedIn("Ada\nLovelace\u0007 ${"x".repeat(100)}", "host", foreground = true)
      val name = store.snapshot.displayName!!
      assertTrue(name.length <= WallpaperStatusStore.MAX_NAME)
      assertTrue(name.none { it.isISOControl() })
      store.signedIn("   ", "host", foreground = true)
      assertNull(store.snapshot.displayName)
      assertEquals(listOf("Signed in", "Server: host", ip), render(WallpaperContext.HOME).lines)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun brandIsValidatedAndFallsBackToTheDefault() {
      store.setBrand("Acme Corp", "#aa5500")
      assertEquals("Acme Corp", store.snapshot.brandName)
      assertEquals("#AA5500", store.snapshot.brandColor)
      store.setBrand("Bad", "red")
      store.setBrand("", "#000000")
      assertEquals("Acme Corp", store.snapshot.brandName)
      assertEquals("#AA5500", store.snapshot.brandColor)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun listenersAreNotifiedUntilRemovedAndOnlyOnRealChange() {
      var calls = 0
      val listener = WallpaperStatusListener { calls++ }
      store.addListener(listener)
      store.signedIn("Ada", null, foreground = true)
      elapsed += 1
      store.signedIn("Ada", null, foreground = true)
      store.clear()
      assertEquals(3, calls)
      store.clear()
      assertEquals(3, calls)
      store.removeListener(listener)
      store.signedIn("Ada", null, foreground = true)
      assertEquals(3, calls)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun networkLineDistinguishesUnknownFromNoAddress() {
      assertEquals("IP: unknown", WallpaperPresentation.networkLine(false, null))
      assertEquals("IP: no network address", WallpaperPresentation.networkLine(true, null))
      assertEquals("IP: wlan0 10.0.0.5", WallpaperPresentation.networkLine(true, "wlan0 10.0.0.5"))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun addressFormattingOmitsLoopbackAndLinkLocalAndPrefersIpv4() {
      assertEquals("wlan0 10.0.0.5, 2001:db8::1", formatNetworkAddresses("wlan0", listOf("fe80::1%wlan0", "2001:db8::1%wlan0", "10.0.0.5", "127.0.0.1", "::1")))
      assertEquals("10.0.0.5", formatNetworkAddresses(null, listOf("10.0.0.5")))
      assertEquals("tun0x 10.8.0.2", formatNetworkAddresses("tun0 \n<x>", listOf("10.8.0.2")))
      assertNull(formatNetworkAddresses("wlan0", listOf("fe80::1%wlan0", "127.0.0.1")))
      assertNull(formatNetworkAddresses("wlan0", emptyList()))
   }
}
