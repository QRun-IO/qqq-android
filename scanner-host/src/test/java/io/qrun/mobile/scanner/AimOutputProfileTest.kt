/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*******************************************************************************
 ** The confirmed AIM output profile removes only a consistent three-character prefix.
 *******************************************************************************/
class AimOutputProfileTest {
   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun splitsConsistentPrefixAndKeepsPayloadExact() {
      assertEquals(AimOutputProfile.Split("]C0", "001234"), AimOutputProfile.split("]C0001234", "LABEL-TYPE-CODE128"))
      assertEquals(AimOutputProfile.Split("]Q1", "001234567890"), AimOutputProfile.split("]Q1001234567890", "QRCODE"))
      assertEquals(AimOutputProfile.Split("]E0", "5901234123457"), AimOutputProfile.split("]E05901234123457", "LABEL-TYPE-EAN13"))
      assertEquals(AimOutputProfile.Split("]E4", "12345670"), AimOutputProfile.split("]E412345670", "EAN8"))
      assertEquals(AimOutputProfile.Split("]d1", " A "), AimOutputProfile.split("]d1 A ", "DATAMATRIX"))
      assertEquals(AimOutputProfile.Split("]X0", "x"), AimOutputProfile.split("]X0x", "LABEL-TYPE-SOMETHING-ELSE"))
   }

   /*******************************************************************************
    ** Only the first three characters are removed, even when the user payload looks like an identifier.
    *******************************************************************************/
   @Test
   fun payloadBeginningWithAnIdentifierIsNotStrippedTwice() {
      assertEquals(AimOutputProfile.Split("]C0", "]C0123"), AimOutputProfile.split("]C0]C0123", "CODE128"))
      assertEquals(AimOutputProfile.Split("]C0", "]Q1abc"), AimOutputProfile.split("]C0]Q1abc", "CODE128"))
      assertEquals(AimOutputProfile.Split("]Q1", "]"), AimOutputProfile.split("]Q1]", "QRCODE"))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun malformedOrInconsistentPrefixIsNotTrusted() {
      for (text in listOf("", "]", "]C", "]C0", "C0001234", "[C0001234", "]00001234", "]C-001234", "]éC0123", " ]C0123", "001234")) {
         assertNull(text, AimOutputProfile.split(text, "CODE128"))
      }
      // A well-formed prefix that belongs to another symbology family means the label or prefix is not what was configured.
      assertNull(AimOutputProfile.split("]Q1001234", "CODE128"))
      assertNull(AimOutputProfile.split("]E0001234567890", "LABEL-TYPE-CODE128"))
      assertNull(AimOutputProfile.split("]C0001234", "QRCODE"))
      assertNull(AimOutputProfile.split("]C0001234", "EAN13"))
   }

   /*******************************************************************************
    ** The capture constructor accepts exactly what the parser produces.
    *******************************************************************************/
   @Test
   fun parsedIdentifierIsAValidCaptureIdentifier() {
      val split = AimOutputProfile.split("]z0Hello", "AZTEC")!!
      val capture = ScannerCapture.of(split.payload, "AZTEC", "]z0Hello".toByteArray(), split.aimIdentifier)
      assertEquals("Hello", capture.barcode)
      assertEquals("]z0", capture.aimIdentifier)
      assertEquals(java.util.Base64.getEncoder().encodeToString("]z0Hello".toByteArray()), capture.rawDataBase64)
   }
}
