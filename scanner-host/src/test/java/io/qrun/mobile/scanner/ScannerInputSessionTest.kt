/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*******************************************************************************
 **
 *******************************************************************************/
class ScannerInputSessionTest {
   /*******************************************************************************
    **
    *******************************************************************************/
   private class Device : ScannerDriver {
      lateinit var listener: ScannerDriver.Listener
      var reads = 0
      var cancels = 0
      var releases = 0
      var pending = false
      var failRead = false
      var failCancel = false
      var failRelease = false

      /*******************************************************************************
       **
       *******************************************************************************/
      override fun open(listener: ScannerDriver.Listener) {
         this.listener = listener
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      override fun isReadPending() = pending

      /*******************************************************************************
       **
       *******************************************************************************/
      override fun read() {
         if (failRead) error("Synthetic read error"); reads++; pending = true
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      override fun cancelRead() {
         cancels++; pending = false; if (failCancel) error("Synthetic cancel error")
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      override fun close() {
         releases++; if (failRelease) error("Synthetic close error")
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      fun idle() {
         listener.idle()
      }

      /*******************************************************************************
       **
       *******************************************************************************/
      fun scan(value: String = "001234567890") {
         pending = false; listener.scanned(value, "QRCODE")
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   private class Fixture {
      val devices = mutableListOf<Device>()
      val scans = mutableListOf<Pair<String, String>>()
      val statuses = mutableListOf<String>()
      val session = ScannerInputSession(
         { Device().also { devices.add(it) } },
         { value, type -> scans.add(value to type) }, { message, _ -> statuses.add(message) })

      /*******************************************************************************
       **
       *******************************************************************************/
      fun start(): Device {
         session.setAccepting(true)
         session.setActive(true)
         return devices.last()
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun missingHardwareKeepsManualFallback() {
      val statuses = mutableListOf<String>()
      val session = ScannerInputSession({ null }, { _, _ -> error("Unexpected scan") }, { message, _ -> statuses.add(message) })
      session.setAccepting(true)
      session.setActive(true)
      assertTrue(statuses.single().contains("manually"))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun liveSurfaceGateRejectsCallbackBeforeRecompositionAndRearmsAfterDismissal() {
      val device = Device()
      var allowed = true
      val scans = mutableListOf<String>()
      val session = ScannerInputSession({ device }, { value, _ -> scans.add(value) }, { _, _ -> }, { allowed })
      session.setAccepting(true)
      session.setActive(true)
      device.idle()
      allowed = false // An overlay opened; Compose has not run setAccepting(false) yet.
      device.scan()
      device.idle()
      assertTrue(scans.isEmpty())
      assertEquals(1, device.reads)
      session.setAccepting(false)
      allowed = true
      session.setAccepting(true)
      assertEquals(2, device.reads)
      device.scan("00123")
      assertEquals(listOf("00123"), scans)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun hiddenSurfaceNeverArmsEvenWhenOldReadinessFlagIsTrue() {
      val device = Device()
      var allowed = false
      val session = ScannerInputSession({ device }, { _, _ -> error("Unexpected scan") }, { _, _ -> }, { allowed })
      session.setAccepting(true)
      session.setActive(true)
      device.idle()
      assertEquals(0, device.reads)
      allowed = true
      session.setAccepting(true)
      assertEquals(1, device.reads)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun waitsForIdleAndDoesNotDuplicateAnArmedRead() {
      val fixture = Fixture()
      val device = fixture.start()
      assertEquals(0, device.reads)
      device.idle()
      device.idle()
      fixture.session.setAccepting(true)
      assertEquals(1, device.reads)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun preservesRawStringAndWaitsForAcknowledgmentBeforeRearming() {
      val fixture = Fixture()
      val device = fixture.start()
      device.idle()
      device.scan(" 001234567890 ")
      device.idle()
      device.scan("injected-duplicate")
      assertEquals(listOf(" 001234567890 " to "QRCODE"), fixture.scans)
      assertEquals(1, device.reads)
      fixture.session.setAccepting(true)
      assertEquals(2, device.reads)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun idleBeforeDataAlsoRearmsAfterAcknowledgment() {
      val fixture = Fixture()
      val device = fixture.start()
      device.idle()
      device.pending = false
      device.idle()
      assertEquals(2, device.reads)
      // Deliver the first read's data while the second read is pending.
      device.listener.scanned("001234567890", "QRCODE")
      assertEquals(1, device.cancels)
      assertEquals(1, fixture.scans.size)
      device.idle()
      fixture.session.setAccepting(true)
      assertEquals(3, device.reads)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun idleWithoutDecodedDataAllowsTheNextTrigger() {
      val fixture = Fixture()
      val device = fixture.start()
      device.idle()
      device.pending = false
      device.idle()
      assertEquals(2, device.reads)
      device.scan()
      assertEquals(1, fixture.scans.size)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun explicitReconnectRecoversAfterOldAcquisitionWasBusy() {
      val fixture = Fixture()
      val first = fixture.start()
      fixture.session.setActive(false)
      fixture.session.setActive(true)
      val busy = fixture.devices.last()
      busy.listener.failed()
      first.idle()
      fixture.session.reconnect()
      val recovered = fixture.devices.last()
      assertEquals(3, fixture.devices.size)
      recovered.idle()
      recovered.scan()
      assertEquals(1, fixture.scans.size)
      assertEquals(1, busy.releases)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun emptyOrCanceledCollectionWaitsForIdleAndAllowsNextRead() {
      val fixture = Fixture()
      val device = fixture.start()
      device.idle()
      device.pending = false
      device.listener.noData(false)
      assertEquals(1, device.reads)
      assertEquals(0, device.releases)
      assertTrue(fixture.scans.isEmpty())
      device.idle()
      assertEquals(2, device.reads)
      device.scan()
      assertEquals(1, fixture.scans.size)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun canceledCollectionAfterAcceptedScanDoesNotClearAcknowledgmentGate() {
      val fixture = Fixture()
      val device = fixture.start()
      device.idle()
      device.pending = false
      device.idle()
      device.listener.scanned("001234567890", "QRCODE")
      device.listener.noData(false)
      device.idle()
      assertEquals(1, device.cancels)
      assertEquals(2, device.reads)
      assertEquals(0, device.releases)
      assertTrue(fixture.statuses.last().contains("Waiting for scan confirmation"))
      fixture.session.setAccepting(true)
      assertEquals(3, device.reads)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun multipleBarcodesAreRejectedWithNoticeAndNextSingleScanWorks() {
      val fixture = Fixture()
      val device = fixture.start()
      device.idle()
      device.pending = false
      device.listener.noData(true)
      device.idle()
      assertTrue(fixture.scans.isEmpty())
      assertEquals(0, device.releases)
      assertEquals(2, device.reads)
      assertTrue(fixture.statuses.last().contains("Multiple barcodes were rejected"))
      device.scan()
      assertEquals(1, fixture.scans.size)
      assertTrue(fixture.statuses.last().contains("Waiting for scan confirmation"))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun vendorPendingReadIsNotDuplicated() {
      val fixture = Fixture()
      val device = fixture.start()
      device.pending = true
      device.idle()
      assertEquals(0, device.reads)
      device.pending = false
      device.idle()
      assertEquals(1, device.reads)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun blockedWorkCancelsReadAndIgnoresLateData() {
      val fixture = Fixture()
      val device = fixture.start()
      device.idle()
      fixture.session.setAccepting(false)
      fixture.session.setAccepting(false)
      device.scan()
      device.idle()
      assertEquals(1, device.cancels)
      assertTrue(fixture.scans.isEmpty())
      fixture.session.setAccepting(true)
      assertEquals(2, device.reads)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun pauseReleasesOnceAndOldGenerationCannotAffectResumedDevice() {
      val fixture = Fixture()
      val first = fixture.start()
      first.idle()
      fixture.session.setActive(false)
      fixture.session.setActive(false)
      fixture.session.setActive(true)
      val second = fixture.devices.last()
      first.idle()
      first.scan()
      first.listener.failed()
      assertEquals(1, first.releases)
      assertEquals(0, second.releases)
      assertTrue(fixture.scans.isEmpty())
      second.idle()
      second.scan()
      assertEquals(1, fixture.scans.size)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun lateReadyAfterDisposeNeverArmsOrSubmits() {
      val fixture = Fixture()
      val device = fixture.start()
      fixture.session.setActive(false)
      device.idle()
      device.scan()
      assertEquals(1, device.releases)
      assertEquals(0, device.reads)
      assertTrue(fixture.scans.isEmpty())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun readFailureReleasesAndDoesNotClaimReady() {
      val fixture = Fixture()
      val device = fixture.start()
      device.failRead = true
      device.idle()
      device.idle()
      fixture.session.setAccepting(true)
      assertEquals(1, device.releases)
      assertEquals(0, device.reads)
      assertTrue(fixture.statuses.last().contains("Reconnect"))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun failedCancellationStillReleasesScanner() {
      val fixture = Fixture()
      val device = fixture.start()
      device.idle()
      device.failCancel = true
      fixture.session.setAccepting(false)
      assertEquals(1, device.releases)
      assertTrue(fixture.statuses.last().contains("Reconnect"))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun failedReleaseDoesNotClaimReadyOrAcquireAnotherDevice() {
      val fixture = Fixture()
      val device = fixture.start()
      device.idle()
      device.failRelease = true
      fixture.session.setActive(false)
      fixture.session.setActive(true)
      assertEquals(1, fixture.devices.size)
      assertTrue(fixture.statuses.last().contains("release could not be confirmed"))
   }
}
