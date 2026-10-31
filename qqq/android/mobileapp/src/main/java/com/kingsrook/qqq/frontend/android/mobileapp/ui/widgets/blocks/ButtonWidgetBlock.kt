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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.core.model.metadata.QIcon
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.BlockStyleUtils
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.DynamicIcon
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.makeIcon

/***************************************************************************
 **
 ***************************************************************************/
@Composable
fun ButtonWidgetBlock(params: WidgetBlockParameters, modifier: Modifier = Modifier, disableControls: Boolean = false)
{
   val widgetBlock = params.widgetBlock
   val actionCallback = params.actionCallback

   val color = BlockStyleUtils.getColorFromString("${widgetBlock.styles["color"]}") ?: Color.Black
   val format = "${widgetBlock.styles["format"]}".toLowerCase(Locale.current)
   val startIcon: QIcon? = makeIcon(widgetBlock.values["startIcon"])
   val endIcon: QIcon? = makeIcon(widgetBlock.values["endIcon"])

   /***************************************************************************
    ** handle the button being clicked - basically, just go to the actionCallback.
    ***************************************************************************/
   fun onClick()
   {
      val values = mutableMapOf("widgetBlock" to widgetBlock)
      if(actionCallback != null)
      {
         if(widgetBlock.values["actionCode"] != null || widgetBlock.values["controlCode"] != null)
         {
            actionCallback(values)
         }
      }
   }

   val textColor = if(format == "outlined" || format == "text") color else Color.White
   val bgColor = if(format == "outlined") Color.White else color

   /***************************************************************************
    ** row of <startIcon> text <endIcon>, to display inside the button
    ***************************************************************************/
   @Composable
   fun ButtonContent()
   {
      Row(verticalAlignment = Alignment.CenterVertically)
      {
         if(startIcon != null)
         {
            DynamicIcon(startIcon, null, tint = textColor, modifier = Modifier.padding(end = 4.dp))
         }

         Text("${widgetBlock.values["label"] ?: widgetBlock.values["actionCode"] ?: "Button"}", color = textColor)

         if(endIcon != null)
         {
            DynamicIcon(endIcon, null, tint = textColor, modifier = Modifier.padding(start = 4.dp))
         }
      }
   }

   val shape = RoundedCornerShape(12.dp)

   ////////////////////////////////////////////////////////
   // choose different button composable based on format //
   ////////////////////////////////////////////////////////
   when (format)
   {
      "outlined" ->
      {
         OutlinedButton(
            onClick = ::onClick,
            modifier = modifier,
            colors = ButtonColors(contentColor = Color.Blue, containerColor = bgColor, disabledContentColor = Color.Black, disabledContainerColor = Color.Gray),
            shape = shape,
            enabled = !disableControls
         )
         {
            ButtonContent()
         }
      }

      "filled" ->
      {
         FilledTonalButton(
            onClick = ::onClick,
            modifier = modifier,
            colors = ButtonColors(contentColor = textColor, containerColor = bgColor, disabledContentColor = Color.Black, disabledContainerColor = Color.Gray),
            shape = shape,
            enabled = !disableControls
         )
         {
            ButtonContent()
         }
      }

      "text" ->
      {
         TextButton(onClick = ::onClick, modifier = modifier, enabled = !disableControls)
         {
            ButtonContent()
         }
      }

      else ->
      {
         Button(onClick = ::onClick, modifier = modifier, shape = shape, enabled = !disableControls)
         {
            ButtonContent()
         }
      }
   }

}