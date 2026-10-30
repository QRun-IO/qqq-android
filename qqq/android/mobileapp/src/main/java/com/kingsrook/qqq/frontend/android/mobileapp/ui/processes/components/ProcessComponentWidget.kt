/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2024.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendComponent
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.widgets.WidgetBlock
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.Colors
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.WidgetBlockComposable
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.WidgetBlockParameters
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel

/***************************************************************************
 ** process-component to render a widget
 ***************************************************************************/
@OptIn(ExperimentalFoundationApi::class) // for stickyHeader
@Composable
fun ProcessComponentWidget(processViewModel: ProcessViewModel, qInstance: QInstance, component: QFrontendComponent, disableControls: Boolean = false)
{
   val state = remember(processViewModel.stepInstanceCounter, component) { determineState(processViewModel, qInstance, component) }

   //////////////////////////////////////
   // initially set footer height to 0 //
   //////////////////////////////////////
   val fixedFooterHeight = remember { mutableStateOf(0.dp) }
   val density = LocalDensity.current

   /***************************************************************************
    ** when a control needs to call back (e.g., to submit or whatever), it
    ** goes through this function
    ***************************************************************************/
   fun actionCallback(eventValues: Map<String, Any>)
   {
      processViewModel.actionCallback(eventValues)
   }

   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   // box to wrap the lazyColumn (which has a built-in stickyHeader) and a manual sticky footer we'll put below it //
   //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
   val boxModifier = if(state.needFullHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
   Box(modifier = boxModifier)
   {
      LazyColumn(Modifier)
      {
         ////////////////////////////////////////////
         // do the sticky header blocks, if needed //
         ////////////////////////////////////////////
         if(state.headerBlockList.isNotEmpty())
         {
            stickyHeader()
            {
               ////////////////////////////////////////////////////////////////
               // add a surface - i think it was so body would scroll behind //
               ////////////////////////////////////////////////////////////////
               Surface()
               {
                  state.headerBlockList.forEach()
                  { widgetBlock ->
                     WidgetBlockComposable(WidgetBlockParameters(widgetBlock, ::actionCallback, processViewModel.qViewModel, processViewModel.processValues), disableControls = disableControls, modifier = Modifier)
                  }
               }
            }
         }

         ////////////////////////
         // do the body blocks //
         ////////////////////////
         items(items = state.bodyBlockList)
         { widgetBlock ->
            WidgetBlockComposable(WidgetBlockParameters(widgetBlock, ::actionCallback, processViewModel.qViewModel, processViewModel.processValues), disableControls = disableControls, modifier = Modifier)
         }

         items(items = state.warningStrings)
         {
            Text(it, color = Colors.WARNING, modifier = Modifier.padding(8.dp))
         }

         item()
         {
            ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // the fixed-footer - will cover the bottom portion of this column - so, put a box here equal to its height, to scroll behind it //
            ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            Box(Modifier.padding(bottom = fixedFooterHeight.value))
         }
      }

      ////////////////////////////////////////////////////////////////////////////////////////////////////////////
      // outside of the scrolling lazyColumn, do a manual sticky footer, if needed                              //
      // credit: https://dev.to/theplebdev/creating-a-quick-sticky-footer-for-lazycolumn-in-jetpack-compose-237 //
      ////////////////////////////////////////////////////////////////////////////////////////////////////////////
      if(state.footerBlockList.isNotEmpty())
      {
         Column(modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .onGloballyPositioned()
            {
               /////////////////////////////////////////////////////////////////////////
               // capture the footer's height, to fix scrolling of the body behind it //
               /////////////////////////////////////////////////////////////////////////
               fixedFooterHeight.value = with(density)
               {
                  it.size.height.toDp()
               }
            }
         )
         {
            ///////////////////////////////////////////
            // surface, for background and solidness //
            ///////////////////////////////////////////
            Surface()
            {
               state.footerBlockList.forEach()
               { widgetBlock ->
                  WidgetBlockComposable(WidgetBlockParameters(widgetBlock, ::actionCallback, processViewModel.qViewModel, processViewModel.processValues), disableControls = disableControls, modifier = Modifier)
               }
            }
         }
      }
   }
}


/***************************************************************************
 ** container for the state within this widget
 ***************************************************************************/
data class ProcessComponentWidgetState(
   val warningStrings: List<String> = mutableListOf(),
   val headerBlockList: List<WidgetBlock> = mutableListOf(),
   val bodyBlockList: List<WidgetBlock> = mutableListOf(),
   val footerBlockList: List<WidgetBlock> = mutableListOf(),
   val needFullHeight: Boolean = false,
)

/***************************************************************************
 ** function to figure out the (initial, and probably only) state of this
 ** widget
 ***************************************************************************/
private fun determineState(processViewModel: ProcessViewModel, qInstance: QInstance, component: QFrontendComponent): ProcessComponentWidgetState
{
   val warningStrings = mutableListOf<String>()
   val headerBlockList = mutableListOf<WidgetBlock>()
   val bodyBlockList = mutableListOf<WidgetBlock>()
   val footerBlockList = mutableListOf<WidgetBlock>()
   val needFullHeight = false

   //////////////////////////////////////////////////////////////////////////////////////////////
   // if this component says it's a list of adhoc widgets, then do some pre-processing on them //
   //////////////////////////////////////////////////////////////////////////////////////////////
   val widgetBlockList = mutableListOf<WidgetBlock>()

   if(component.values["isAdHocWidget"] == true)
   {
      val blocks = component.values["blocks"]
      if(blocks is List<*>)
      {
         blocks.forEach()
         { block ->
            val widgetBlock = WidgetBlock.fromMap(block)
            if(widgetBlock != null)
            {
               widgetBlockList.add(widgetBlock)
            }
         }
      }
      else
      {
         warningStrings.add("Widget blocks was not an expected type.")
      }

      evaluateWidgetBlocks(widgetBlockList, processViewModel.processValues)
   }
   else if(component.values["widgetName"] != null)
   {
      val widgetName = "${component.values["widgetName"]}"
      val widgetMetaData = qInstance.widgets?.get(widgetName)
      if(widgetMetaData == null)
      {
         warningStrings.add("Undefined widget: ${widgetName}")
      }
      else
      {
         if(widgetMetaData.type == "composite")
         {
            val widgetData = processViewModel.processValues[widgetName]
            var foundBlocks = false
            if(widgetData is Map<*, *>)
            {
               val blocks = widgetData["subBlocks"]
               if(blocks is List<*>)
               {
                  blocks.forEach()
                  { block ->
                     val widgetBlock = WidgetBlock.fromMap(block)
                     if(widgetBlock != null)
                     {
                        widgetBlockList.add(widgetBlock)
                        foundBlocks = true
                     }
                  }
               }
            }

            if(!foundBlocks)
            {
               warningStrings.add("Could not find widget block data in process.")
            }
         }
         else
         {
            warningStrings.add("Not-yet implemented widget type: ${widgetMetaData.type}")
         }
      }
   }
   else
   {
      warningStrings.add("Missing required values in a wiget component")
   }

   /////////////////////////////////////////////////////////////////////////////////////////
   // split into body & footer lists                                                      //
   // todo no no, this should be done based on some attribute in the widget/blocks/etc... //
   /////////////////////////////////////////////////////////////////////////////////////////
   bodyBlockList.addAll(widgetBlockList)
   /*
   if(bodyBlockList.size > 2)
   {
      headerBlockList.add(bodyBlockList.removeAt(0))
      footerBlockList.add(bodyBlockList.removeAt(bodyBlockList.size - 1))
      needFullHeight = true
   }
   */

   return ProcessComponentWidgetState(warningStrings, headerBlockList, bodyBlockList, footerBlockList, needFullHeight)
}

/***************************************************************************
 ** applies "conditional" logic to blocks; interpolate values too.
 ***************************************************************************/
fun evaluateWidgetBlocks(widgetBlockList: MutableList<WidgetBlock>, processValues: MutableMap<String, Any?>)
{
   val iterator = widgetBlockList.iterator()
   while(iterator.hasNext())
   {
      val widgetBlock = iterator.next()

      ////////////////////////////////////////////////////////////////////
      // if the block has a conditional, evaluate, and remove if untrue //
      ////////////////////////////////////////////////////////////////////
      if(widgetBlock.conditional != null)
      {
         val value = processValues[widgetBlock.conditional]
         if(value == null || value == "" || value == false)
         {
            iterator.remove()
            continue
         }
      }

      if(widgetBlock.blockType == "COMPOSITE")
      {
         /////////////////////////////////////////
         // make recursive calls for composites //
         /////////////////////////////////////////
         val mutableSubBlocks = widgetBlock.subBlocks.toMutableList()
         evaluateWidgetBlocks(mutableSubBlocks, processValues)
         widgetBlock.subBlocks = mutableSubBlocks
      }
      else if(widgetBlock.blockType == "INPUT_FIELD")
      {
         /////////////////////////////////////////////////////////////////////////////////////////////////////////////
         // for input fields, put the process's value for the field-name into the block's values object as '.value' //
         // todo QFMD was doing something here, but we didn't do it in qqq-android yet...                           //
         /////////////////////////////////////////////////////////////////////////////////////////////////////////////
         // val fieldMetaData = widgetBlock.values["fieldMetaData"]
         // if(fieldMetaData != null && fieldMetaData is Map<*, *>)
         // {
         //    val fieldName = fieldMetaData["name"]
         //    if(processValues.containsKey(fieldName))
         //    {
         //       widgetBlock.values["value"] = processValues[fieldName]
         //    }
         // }
      }
      else if(widgetBlock.blockType == "TEXT")
      {
         var text = widgetBlock.values["text"]
         if(text != null && text is String)
         {
            for(entry in processValues.entries)
            {
               text = (text as String).replace('$' + "{" + entry.key + "}", "${processValues[entry.key]}")
            }
         }
         widgetBlock.values["interpolatedText"] = text
      }
   }
}

