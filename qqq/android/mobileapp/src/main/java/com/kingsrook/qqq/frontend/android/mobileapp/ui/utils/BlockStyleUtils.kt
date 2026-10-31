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
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/***************************************************************************
 ** utility functions for working with styles in blocks
 ***************************************************************************/
object BlockStyleUtils
{

   /***************************************************************************
    ** read a padding map, from a style map, reading top/bottom/left/right out
    ** of it.  only (optional) numeric values supported today.
    ***************************************************************************/
   fun getPaddingValuesFromStyleMap(map: Map<*, *>): PaddingValues
   {
      val paddingStyle = map["padding"]
      return if(paddingStyle is Map<*, *>)
      {
         PaddingValues(
            top = getIntFromMap(paddingStyle, "top", 0).dp,
            bottom = getIntFromMap(paddingStyle, "bottom", 0).dp,
            start = getIntFromMap(paddingStyle, "left", 0).dp,
            end = getIntFromMap(paddingStyle, "right", 0).dp
         )
      }
      else
      {
         PaddingValues()
      }
   }

   /***************************************************************************
    ** read width & height from a style map.  only (optional) numeric values
    ** supported today.
    ***************************************************************************/
   fun getSizeFromStyleMap(map: Map<*, *>): DpSize
   {
      val width = getOptionalIntFromMap(map, "width")
      val height = getOptionalIntFromMap(map, "height")
      return if(width != null && height != null)
      {
         DpSize(width.dp, height.dp)
      }
      else if(width != null)
      {
         DpSize(width.dp, Dp.Unspecified)
      }
      else if(height != null)
      {
         DpSize(Dp.Unspecified, height.dp)
      }
      else
      {
         DpSize(Dp.Unspecified, Dp.Unspecified)
      }
   }


   /***************************************************************************
    ** Get a Color from a string.  Some named colors are supported, as well as
    ** html-style RGB/RGBA, with optional # prefix.
    ***************************************************************************/
   fun getColorFromString(input: String?): Color?
   {
      return when (val string = "${input}".toUpperCase(Locale.current))
      {
         "SUCCESS" -> Colors.SUCCESS
         "WARNING" -> Colors.WARNING
         "ERROR" -> Colors.ERROR
         "INFO" -> Colors.INFO
         "MUTED" -> Colors.MUTED
         else ->
         {
            var hexString = string
            val sixToEightHexDigits = "[0-9A-F]{6,8}"
            if(hexString.matches(Regex("^#${sixToEightHexDigits}$")))
            {
               hexString = hexString.substring(1)
            }

            if(hexString.matches(Regex("^${sixToEightHexDigits}$")))
            {
               val r = Integer.parseInt(hexString, 0, 2, 16)
               val g = Integer.parseInt(hexString, 2, 4, 16)
               val b = Integer.parseInt(hexString, 4, 6, 16)
               val a = try
               {
                  Integer.parseInt(hexString, 6, 8, 16)
               }
               catch(e: Exception)
               {
                  255
               }
               Color(r, g, b, a)
            }
            else
            {
               null
            }
         }
      }
   }


   /***************************************************************************
    **
    ***************************************************************************/
   fun getOptionalIntFromMap(map: Map<*, *>, key: String): Int?
   {
      val valueString = "${map[key] ?: ""}"

      return if(valueString == "")
      {
         null
      }
      else
      {
         try
         {
            Integer.parseInt(valueString)
         }
         catch(e: Exception)
         {
            null
         }
      }
   }


   /***************************************************************************
    **
    ***************************************************************************/
   fun getOptionalStringFromMap(map: Map<*, *>, key: String): String?
   {
      val valueString = "${map[key] ?: ""}"

      return if(valueString == "")
      {
         null
      }
      else
      {
         valueString
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   fun getIntFromMap(map: Map<*, *>, key: String, default: Int): Int
   {
      val valueString = "${map[key] ?: ""}"

      return if(valueString == "")
      {
         default
      }
      else
      {
         try
         {
            Integer.parseInt(valueString)
         }
         catch(e: Exception)
         {
            default
         }
      }
   }

}