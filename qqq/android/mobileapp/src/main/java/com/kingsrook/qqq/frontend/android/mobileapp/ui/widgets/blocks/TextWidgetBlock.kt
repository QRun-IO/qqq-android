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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingsrook.qqq.frontend.android.core.model.metadata.QIcon
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.BlockStyleUtils
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.DynamicIcon
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.makeIcon

/***************************************************************************
 **
 ***************************************************************************/
@Composable
fun TextWidgetBlock(params: WidgetBlockParameters, modifier: Modifier = Modifier)
{
   val widgetBlock = params.widgetBlock

   val color = BlockStyleUtils.getColorFromString("${widgetBlock.styles["color"]}") ?: Color.Black

   val weight: FontWeight = when (widgetBlock.styles["weight"])
   {
      "bold" -> FontWeight.Bold
      "semibold" -> FontWeight.SemiBold
      "black" -> FontWeight.Black
      "thin" -> FontWeight.Thin
      "extrabold" -> FontWeight.ExtraBold
      "extralight" -> FontWeight.ExtraLight
      "medium" -> FontWeight.Medium
      "100" -> FontWeight.W100
      "200" -> FontWeight.W200
      "300" -> FontWeight.W300
      "400" -> FontWeight.W400
      "500" -> FontWeight.W500
      "600" -> FontWeight.W600
      "700" -> FontWeight.W700
      "800" -> FontWeight.W800
      "900" -> FontWeight.W900
      else -> FontWeight.Normal
   }

   val size = when (val blockStyleSize = "${widgetBlock.styles["size"]}")
   {
      "largest" -> Typography().displayMedium.fontSize // 45
      "headline" -> Typography().headlineMedium.fontSize // 28
      "title" -> Typography().titleLarge.fontSize // 22
      "body" -> Typography().bodyLarge.fontSize // 16
      "smallest" -> Typography().labelMedium.fontSize // 12
      else ->
      {
         val isNumeric = blockStyleSize.all { char -> char.isDigit() }
         if(isNumeric)
         {
            Integer.parseInt(blockStyleSize).sp
         }
         else
         {
            Typography().bodyLarge.fontSize
         }
      }
   }

   val lineHeight = size * 1.2

   ////////////////////////////////////////////////////////////////////////////////////////
   // if there's interpolatedText, use that - else use the text value, else empty string //
   ////////////////////////////////////////////////////////////////////////////////////////
   val text = "${widgetBlock.values["interpolatedText"] ?: widgetBlock.values["text"] ?: ""}"
   val startIcon: QIcon? = makeIcon(widgetBlock.values["startIcon"])
   val endIcon: QIcon? = makeIcon(widgetBlock.values["endIcon"])

   /////////////////////////////////////////////////////////////////////
   // set attributes that need to go on the text, based on the format //
   /////////////////////////////////////////////////////////////////////
   var textPadding = 0.dp
   var horizontalArrangement = Arrangement.Start

   if(widgetBlock.styles["format"] == "alert")
   {
      textPadding = 8.dp
   }
   else if(widgetBlock.styles["format"] == "banner")
   {
      horizontalArrangement = Arrangement.Center
      textPadding = 8.dp
   }

   /////////////////////////////////////////////
   // build the stuff that'll go inside a box //
   /////////////////////////////////////////////
   @Composable
   fun TextComposable()
   {
      Row(horizontalArrangement = horizontalArrangement, modifier = Modifier.padding(textPadding))
      {
         if(startIcon != null)
         {
            DynamicIcon(startIcon, null, tint = color, modifier = Modifier.padding(end = 4.dp))
         }

         Text(text, color = color, fontWeight = weight, fontSize = size, lineHeight = lineHeight)

         if(endIcon != null)
         {
            DynamicIcon(endIcon, null, tint = color, modifier = Modifier.padding(start = 4.dp))
         }
      }
   }

   //////////////////
   // final output //
   //////////////////
   if(widgetBlock.styles["format"] == "alert")
   {
      Box(
         modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .background(Brush.horizontalGradient(listOf(color, color)), RoundedCornerShape(4.dp), 0.2f)
      )
      { TextComposable() }
   }
   else if(widgetBlock.styles["format"] == "banner")
   {
      Box(
         contentAlignment = Alignment.Center, modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(color, color)), RectangleShape, 0.2f)
      )
      { TextComposable() }
   }
   else
   {
      TextComposable()
   }

}

