/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2024.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.kingsrook.qqq.frontend.android.mobileapp.ui.utils

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/***************************************************************************
 **
 ***************************************************************************/
class BlockStyleUtilsTest
{

   /***************************************************************************
    **
    ***************************************************************************/
   @Test
   fun testGetPaddingValuesFromStyleMap()
   {
      fun doGetPaddingValues(map: Map<*, *>) = BlockStyleUtils.getPaddingValuesFromStyleMap(mapOf("padding" to map))

      assertEquals(PaddingValues(1.dp, 2.dp, 3.dp, 4.dp), doGetPaddingValues(mapOf("left" to 1, "top" to 2, "right" to 3, "bottom" to 4)))
      assertEquals(PaddingValues(), doGetPaddingValues(emptyMap<String, Any?>()))
      assertEquals(PaddingValues(10.dp, 20.dp, 30.dp, 0.dp), doGetPaddingValues(mapOf("left" to "10", "top" to "20", "right" to "30")))
      assertEquals(PaddingValues(0.dp, 0.dp, 0.dp, 0.dp), doGetPaddingValues(mapOf("left" to "left", "top" to "0x12", "right" to mapOf("a" to "b"))))
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Test
   fun testGetSizeValuesFromStyleMap()
   {
      fun doGetSizeValues(map: Map<*, *>) = BlockStyleUtils.getSizeFromStyleMap(map)

      assertEquals(DpSize(1.dp, 2.dp), doGetSizeValues(mapOf("width" to 1, "height" to 2)))
      assertEquals(DpSize(Dp.Unspecified, 2.dp), doGetSizeValues(mapOf("height" to 2)))
      assertEquals(DpSize(1.dp, Dp.Unspecified), doGetSizeValues(mapOf("width" to 1)))
      assertEquals(DpSize(Dp.Unspecified, Dp.Unspecified), doGetSizeValues(emptyMap<String, Any?>()))
      assertEquals(DpSize(Dp.Unspecified, Dp.Unspecified), doGetSizeValues(mapOf("width" to "a", "height" to mapOf("a" to "z"))))
   }


   /***************************************************************************
    **
    ***************************************************************************/
   @Test
   fun testGetColorFromString()
   {
      assertEquals(Color(1, 2, 3), BlockStyleUtils.getColorFromString("010203"))
      assertEquals(Color(10, 11, 12), BlockStyleUtils.getColorFromString("#0A0B0C"))
      assertEquals(Color(0xFF, 0xFE, 0xFD, 0xFC), BlockStyleUtils.getColorFromString("#FFFEFDFC"))
      assertEquals(Color(0x80, 0x80, 0x80, 0x80), BlockStyleUtils.getColorFromString("80808080"))
      assertEquals(Colors.SUCCESS, BlockStyleUtils.getColorFromString("SUCCESS"))
      assertEquals(Colors.WARNING, BlockStyleUtils.getColorFromString("Warning"))
      assertEquals(Colors.ERROR, BlockStyleUtils.getColorFromString("error"))
      assertEquals(Colors.ERROR, BlockStyleUtils.getColorFromString("error"))
      assertNull(BlockStyleUtils.getColorFromString("123"))
      assertNull(BlockStyleUtils.getColorFromString("red"))
   }

}