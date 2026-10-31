package io.qrun.mobile.scanner

import org.junit.Assert.*
import org.junit.Test

class ScannerMetaDataTest
{
   @Test fun serverDefinesLabelsAndLengthWithContractDefaults()
   {
      assertEquals(ScannerMetaData(), ScannerMetaData.parse(emptyMap()))
      assertEquals(ScannerMetaData("Item barcode", "Save item", 30), ScannerMetaData.parse(mapOf("manualLabel" to "Item barcode", "submitLabel" to "Save item", "maxLength" to 30L, "future" to true)))
   }

   @Test fun explicitlyInvalidLengthFailsClosedInsteadOfBroadeningTheConstraint()
   {
      for(value in listOf(null, -1, 0, 4097, "10", 1.5, Double.NaN, Double.POSITIVE_INFINITY, Long.MAX_VALUE))
      {
         assertThrows(IllegalArgumentException::class.java) { ScannerMetaData.parse(mapOf("maxLength" to value)) }
      }
   }
}
