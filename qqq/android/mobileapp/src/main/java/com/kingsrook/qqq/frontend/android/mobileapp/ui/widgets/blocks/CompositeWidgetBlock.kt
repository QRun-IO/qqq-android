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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kingsrook.qqq.frontend.android.core.model.widgets.WidgetBlock
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.BlockStyleUtils

/***************************************************************************
 ** render a block of type "Composite" - which is - a block that contains
 ** child (sub) blocks.
 ***************************************************************************/
@Composable
fun CompositeWidgetBlock(params: WidgetBlockParameters, modifier: Modifier = Modifier, disableControls: Boolean = false)
{
   val widgetBlock = params.widgetBlock

   if(widgetBlock.modalMode == null)
   {
      ContentsInLayout(params, disableControls)
   }
   else
   {
      when (widgetBlock.modalMode)
      {
         "MODAL" ->
         {
            Modal(params, disableControls)
         }
      }
   }
}

/***************************************************************************
 ** render the list of sub-blocks
 ***************************************************************************/
@Composable
fun BlockList(params: WidgetBlockParameters, subBlocks: List<WidgetBlock>, disableControls: Boolean = false)
{
   for(subBlock in subBlocks)
   {
      val subParams = WidgetBlockParameters(subBlock, params.actionCallback, params.qViewModel, params.values)
      WidgetBlockComposable(subParams, Modifier.padding(4.dp), disableControls)
   }
}

/***************************************************************************
 ** apply layout-specific formatting, to wrap a block-list
 ***************************************************************************/
@OptIn(ExperimentalLayoutApi::class) // for FlowRow
@Composable
fun ContentsInLayout(params: WidgetBlockParameters, disableControls: Boolean = false)
{
   val widgetBlock = params.widgetBlock

   /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   // start with a kinda blank modifier (but, we have to call .padding, or else we get a type that we can't call .padding on later??) //
   /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   var boxModifier = Modifier.padding(0.dp)

   val backgroundColor = BlockStyleUtils.getColorFromString(BlockStyleUtils.getOptionalStringFromMap(widgetBlock.styles, "backgroundColor"))
   if(backgroundColor != null)
   {
      boxModifier = boxModifier.background(backgroundColor, RectangleShape)
   }

   boxModifier = boxModifier.padding(BlockStyleUtils.getPaddingValuesFromStyleMap(widgetBlock.styles))

   // TABLE_SUB_ROW_DETAILS, BADGES_WRAPPER
   when (widgetBlock.layout)
   {
      "FLEX_ROW_CENTER" -> FlowRow(horizontalArrangement = Arrangement.Center, modifier = boxModifier.fillMaxWidth())
      {
         BlockList(params, widgetBlock.subBlocks, disableControls)
      }

      "FLEX_ROW_SPACE_BETWEEN" -> Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = boxModifier.fillMaxWidth())
      {
         BlockList(params, widgetBlock.subBlocks, disableControls)
      }

      "FLEX_ROW" -> Row(modifier = boxModifier.fillMaxWidth())
      {
         BlockList(params, widgetBlock.subBlocks, disableControls)
      }

      "FLEX_ROW_WRAPPED" -> FlowRow(modifier = boxModifier.fillMaxWidth())
      {
         BlockList(params, widgetBlock.subBlocks, disableControls)
      }

      "FLEX_COLUMN" -> Column(modifier = boxModifier.fillMaxWidth())
      {
         BlockList(params, widgetBlock.subBlocks, disableControls)
      }

      else -> Box(modifier = boxModifier)
      {
         BlockList(params, widgetBlock.subBlocks, disableControls)
      }
   }
}

/***************************************************************************
 ** if the composable is modal, wrap it accordingly
 ***************************************************************************/
@Composable
fun Modal(params: WidgetBlockParameters, disableControls: Boolean = false)
{
   val isModalShown = remember { mutableStateOf(params.values[params.widgetBlock.blockId] == true) }

   /***************************************************************************
    ** define a callback function that the view-model can call, to tell us that
    ** the modal's visible state should change
    ***************************************************************************/
   fun controlCallback()
   {
      ////////////////////////////////////////////////////////
      // todo - is toggle always appropriate here?          //
      // if not, i suppose, callback needs to take a map... //
      ////////////////////////////////////////////////////////
      isModalShown.value = !isModalShown.value
   }

   ////////////////////////////////////////////////////////////////////////////
   // upon initial composition, register the callback, based on our block id //
   ////////////////////////////////////////////////////////////////////////////
   LaunchedEffect(Unit)
   {
      params.actionCallback?.invoke(
         mapOf(
            "registerControlCallbackName" to params.widgetBlock.blockId,
            "registerControlCallbackFunction" to ::controlCallback
         )
      )
   }

   key(params.values[params.widgetBlock.blockId])
   {
      if(isModalShown.value)
      {
         Dialog(
            onDismissRequest =
            {
               ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
               // go through the action-callback - so it can internally adjust whatever it needs to track if we are visible //
               ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
               val eventValues = mapOf("controlCode" to "hideModal:${params.widgetBlock.blockId}")
               params.actionCallback?.invoke(eventValues)
               isModalShown.value = false
            },

            ////////////////////////////////////////////////////////
            // this line lets us go full-width (esp on landscape) //
            ////////////////////////////////////////////////////////
            properties = DialogProperties(usePlatformDefaultWidth = false)
         )
         {
            Card(
               modifier = Modifier
                  .fillMaxWidth()
                  .padding(8.dp),
               shape = RoundedCornerShape(16.dp),
            )
            {
               LazyColumn()
               {
                  item()
                  {
                     ContentsInLayout(params, disableControls)
                  }
               }
            }
         }
      }
   }
}