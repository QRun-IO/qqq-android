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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppNodeType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QIcon
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.mobileapp.R
import com.kingsrook.qqq.frontend.android.mobileapp.ui.QNavigator
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.DynamicIcon
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel

/***************************************************************************
 ** Standard screen for an app - shows icons in a grid.
 ***************************************************************************/
@OptIn(ExperimentalLayoutApi::class) // for FlowRow
@Composable
fun QAppHomeGridLayout(qInstance: QInstance, modifier: Modifier = Modifier, app: QAppMetaData? = null, qNavigator: QNavigator? = null)
{
   val childList = app?.let { app.children } ?: qInstance.appTree
   val widgetList = app?.let { app.widgets } ?: emptyList()

   val childApps = childList.filter { child -> child.type == QAppNodeType.APP }
   val sectionNames = app?.sections.orEmpty().flatMap { it.tables.orEmpty() + it.processes.orEmpty() + it.reports.orEmpty() }.toSet()
   val otherChildren = childList.filter { it.type != QAppNodeType.APP && it.name !in sectionNames }

   val isNotYetImplementedDialogOpen = remember { mutableStateOf(false) }
   val notYetImplementedDialogTitle = remember { mutableStateOf("") }
   val notYetImplementedDialogBody = remember { mutableStateOf("") }

   val generalSize = 100

   LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp))
   {
      ///////////////////////////////////////////////////////////////////////////////////////////////////
      // at this point in time, we're doing the title in the topBar, so, redundant to be on-screen too //
      ///////////////////////////////////////////////////////////////////////////////////////////////////
      /*
      item()
      {
         Text(
            title,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
               .padding(8.dp)
               .testTag("appHome.appLabel")
         )
      }
      */

      if(widgetList.isNotEmpty())
      {
         item { Text("This app includes home widgets unavailable in this client.", Modifier.padding(8.dp)) }
      }
      if(childList.isEmpty() && app?.sections?.isEmpty() != false && widgetList.isEmpty())
      {
         item()
         {
            Text(
               "You do not have access to anything in this app.",
               fontSize = 16.sp,
               fontStyle = FontStyle.Italic,
               modifier = Modifier
                  .padding(8.dp)
                  .testTag("appHome.noAccess")
            )
         }
      }

      item()
      {
         FlowRow(modifier = Modifier.fillMaxWidth())
         {
            ///////////////////////////
            // first show child-apps //
            ///////////////////////////
            for(childApp in childApps)
            {
               Tile(childApp.label, childApp.icon, generalSize, "appHome.rowForApp:" + childApp.name)
               {
                  qNavigator?.navigateToApp(childApp.name, childApp.label)
               }
            }
            for(child in otherChildren)
            {
               val processViewModel: ProcessViewModel = viewModel(factory = ProcessViewModel.factory)
               Tile(child.label, child.icon, generalSize, "appHome.rowFor${child.type}:" + child.name)
               {
                  if(child.type == QAppNodeType.PROCESS) qNavigator?.navigateToProcess(child.name, child.label, processViewModel)
                  else
                  {
                     notYetImplementedDialogTitle.value = child.label
                     notYetImplementedDialogBody.value = "This type of screen is unavailable in this client."
                     isNotYetImplementedDialogOpen.value = true
                  }
               }
            }
         }
      }

      ////////////////////////////////////////////////////
      // app sections (with tables, processes, reports) //
      ////////////////////////////////////////////////////
      app?.sections?.forEach()
      { section ->
         if(section.hasContent())
         {
            item()
            {
               FlowRow(Modifier.fillMaxWidth())
               {
                  section.apps.orEmpty().forEach { name ->
                     qInstance.apps?.get(name)?.let { child ->
                        Tile(child.label, child.icon, generalSize, "appHome.sectionApp:" + name) { qNavigator?.navigateToApp(name, child.label) }
                     }
                  }
                  section.reports.orEmpty().forEach { name ->
                     val label = childList.firstOrNull { it.name == name && it.type == QAppNodeType.REPORT }?.label ?: "Report"
                     Text("$label: Reports are unavailable in this client.", Modifier.padding(8.dp))
                  }
               }
            }
            item()
            {
               Text(
                  section.label, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier
                     .padding(horizontal = 8.dp, vertical = 4.dp)
               )
            }

            ////////////
            // tables //
            ////////////
            item()
            {
               FlowRow(modifier = Modifier.fillMaxWidth())
               {
                  section.tables?.forEach()
                  { tableName ->
                     val table = qInstance.tables?.get(tableName)
                     table?.let()
                     {
                        Tile(table.label, table.icon, generalSize, "appHome.rowForTable:" + tableName)
                        {
                           notYetImplementedDialogTitle.value = table.label
                           notYetImplementedDialogBody.value = "Tables are not yet supported in this application frontend."
                           isNotYetImplementedDialogOpen.value = true
                        }
                     }
                  }
               }
            }

            ///////////////
            // processes //
            ///////////////
            item()
            {
               FlowRow(modifier = Modifier.fillMaxWidth())
               {
                  section.processes?.forEach()
                  { processName ->
                     val process = qInstance.processes?.get(processName)
                     process?.let()
                     {
                        val processViewModel: ProcessViewModel = viewModel(factory = ProcessViewModel.factory)
                        Tile(process.label, process.icon, generalSize, "appHome.rowForProcess:" + processName)
                        {
                           qNavigator?.navigateToProcess(process.name, process.label, processViewModel)
                        }
                     }
                  }
               }
            }
         }
      }
   }

   if(isNotYetImplementedDialogOpen.value)
   {
      NotYetImplementedDialog(notYetImplementedDialogTitle.value, notYetImplementedDialogBody.value, isNotYetImplementedDialogOpen)
   }

}

/***************************************************************************
 **
 ***************************************************************************/
@Composable
private fun Tile(label: String, icon: QIcon?, generalSize: Int, testTag: String, onClick: () -> Unit)
{
   Column(
      modifier = Modifier
         .padding(8.dp)
         .width(generalSize.dp)
         .testTag(testTag)
         .clickable { onClick() }
   )
   {
      Box(
         modifier = Modifier
            .fillMaxWidth()
            .width((generalSize - 12).dp)
            .height(generalSize.dp)
            .align(Alignment.CenterHorizontally)
      )
      {
         Box(Modifier
            .background(Color(0xFe, 0xFe, 0xFe), RoundedCornerShape(16.dp))
            .border(2.dp, Color(0xf0, 0xf0, 0xf0), RoundedCornerShape(16.dp))
            .padding(12.dp))
         {
            DynamicIcon(icon, R.drawable.apps, Modifier.fillMaxSize())
         }
      }
      Text(
         label,
         softWrap = true,
         fontSize = 12.sp,
         lineHeight = 18.sp,
         textAlign = TextAlign.Center,
         maxLines = 3,
         overflow = TextOverflow.Ellipsis,
         modifier = Modifier.fillMaxWidth()
      )
   }
}



/***************************************************************************
 **
 ***************************************************************************/
@Composable
fun NotYetImplementedDialog(title: String, body: String, isOpen: MutableState<Boolean>)
{
   AlertDialog(
      icon = { Icon(imageVector = Icons.Default.Info, contentDescription = "Info") },
      title = { Text(title) },
      text = { Text(body) },
      onDismissRequest = { isOpen.value = false },
      dismissButton = { TextButton(onClick = { isOpen.value = false }) { Text("Dismiss") } },
      confirmButton = { }
   )
}
