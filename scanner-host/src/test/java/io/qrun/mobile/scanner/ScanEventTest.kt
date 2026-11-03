/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import com.kingsrook.qqq.frontend.android.core.controllers.UiApiExtension
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiVersion
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/*******************************************************************************
 ** Retrying a submission must not turn it into a second captured scan.
 *******************************************************************************/
class ScanEventTest {
   private val legacyKeys = setOf("barcode", "symbology", "scanSource", "eventId", "capturedAt")

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun version(scanners: String?, path: String = "/qbit/mobile-scanners") = UiApiVersion(
      "1.8.0", extensions = listOfNotNull(scanners?.let { UiApiExtension("mobile-scanners", it, path) })
   )

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun retryKeepsIdentityAndOriginalCaptureTime() {
      val event = ScanEvent("001234", "LABEL-TYPE-CODE128", "scanner")
      assertEquals(event.values(), event.values())
      assertEquals("001234", event.values()["barcode"])
      assertNotEquals(event.eventId, ScanEvent("001234", "LABEL-TYPE-CODE128", "scanner").eventId)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun oldServerGetsExactlyTheLegacyFiveKeysEvenWhenFidelityIsCaptured() {
      val event = ScanEvent(ScannerCapture.of("A", "QRCODE", byteArrayOf(1, 2), "]Q1"), "zebra-emdk")
      assertEquals(legacyKeys, event.values().keys)
      assertEquals(legacyKeys, event.values(includeFidelity = false).keys)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun fidelityValuesAreFlatStringsAndMissingKeysAreOmitted() {
      val raw = byteArrayOf(0x1d, 0x41)
      val full = ScanEvent(ScannerCapture.of("A", "QRCODE", raw, "]Q1"), "zebra-emdk").values(true)
      assertEquals("]Q1", full["aimIdentifier"])
      assertEquals(Base64.getEncoder().encodeToString(raw), full["rawDataBase64"])
      val rawOnly = ScanEvent(ScannerCapture.of("A", "QRCODE", raw), "zebra-emdk").values(true)
      assertEquals(legacyKeys + "rawDataBase64", rawOnly.keys)
      assertEquals(legacyKeys, ScanEvent(ScannerCapture.manual("A"), "manual").values(true).keys)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun retryPayloadIsUnchangedAcrossRepeatedValueBuilding() {
      val source = byteArrayOf(0, 1, 2)
      val event = ScanEvent(ScannerCapture.of("A", "QRCODE", source, "]Q1"), "zebra-emdk")
      val first = event.values(true)
      source[0] = 9
      assertEquals(first, event.values(true))
      assertEquals(first, event.copy().values(true))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun captureCopiesInputAndAllByteValuesRoundTrip() {
      val all = ByteArray(256) { it.toByte() }
      val capture = ScannerCapture.of("A", "QRCODE", all)
      all.fill(0)
      val expected = ByteArray(256) { it.toByte() }
      assertArrayEquals(expected, Base64.getDecoder().decode(capture.rawDataBase64))
      assertEquals(capture, ScannerCapture.of("A", "QRCODE", expected))
      assertEquals(capture.hashCode(), ScannerCapture.of("A", "QRCODE", expected).hashCode())
      assertNotEquals(capture, ScannerCapture.of("A", "QRCODE", expected, "]Q1"))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun rawLengthLimitsOmitInsteadOfTruncating() {
      assertNull(ScannerCapture.of("A", "Q", null).rawDataBase64)
      assertNull(ScannerCapture.of("A", "Q", ByteArray(0)).rawDataBase64)
      assertNull(ScannerCapture.of("A", "Q", ByteArray(8193)).rawDataBase64)
      val max = ScannerCapture.of("A", "Q", ByteArray(8192) { 1 })
      assertEquals(10924, max.rawDataBase64!!.length)
      assertEquals("AA==", ScannerCapture.of("A", "Q", ByteArray(1)).rawDataBase64)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun aimMustBeOriginalWellFormedIdentifier() {
      listOf("]Q1", "]c0", "]A0", "]z9").forEach { assertEquals(it, ScannerCapture.of("A", "Q", null, it).aimIdentifier) }
      listOf("", "Q1", "]Q", "]Q11", "]11", "]Q!", "]Q1 ", "]Qé").forEach {
         assertThrows(IllegalArgumentException::class.java) { ScannerCapture.of("A", "Q", null, it) }
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun stringFormsNeverLeakScanContent() {
      val event = ScanEvent(ScannerCapture.of("SECRET-BARCODE", "QRCODE", "SECRET-RAW".toByteArray(), "]Q1"), "zebra-emdk")
      listOf(event.toString(), event.capture.toString()).forEach {
         assertFalse(it, it.contains("SECRET"))
         assertFalse(it, it.contains(Base64.getEncoder().encodeToString("SECRET-RAW".toByteArray())))
         assertFalse(it, it.contains("]Q1"))
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun capabilityRequiresMobileScanners15WithinMajor1AtRegisteredPath() {
      assertTrue(ScanFidelityCapability.supports(version("1.5.0")))
      assertTrue(ScanFidelityCapability.supports(version("1.12.3")))
      listOf("1.4.9", "1.1.0", "2.0.0", "2.5.0", "1.5", "1.5.0-rc1", "01.5.0", "1.5.0.1", "", "1.99999999999.0").forEach {
         assertFalse(it, ScanFidelityCapability.supports(version(it)))
      }
      assertFalse(ScanFidelityCapability.supports(version("1.5.0", "/qbit/other")))
      assertFalse(ScanFidelityCapability.supports(version(null)))
      assertFalse(ScanFidelityCapability.supports(null))
      val duplicate = UiApiVersion(
         "1.8.0", extensions = listOf(
            UiApiExtension("mobile-scanners", "1.5.0", "/qbit/mobile-scanners"),
            UiApiExtension("mobile-scanners", "1.5.0", "/qbit/mobile-scanners")
         )
      )
      assertFalse(ScanFidelityCapability.supports(duplicate))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun capabilityIsScopedToTheServerThatWasDiscovered() {
      val v = version("1.5.0")
      assertTrue(ScanFidelityCapability.available("https://a/", "https://a/", v))
      assertFalse(ScanFidelityCapability.available("https://b/", "https://a/", v))
      assertFalse(ScanFidelityCapability.available("https://a/", "https://a/", null))
   }
}
