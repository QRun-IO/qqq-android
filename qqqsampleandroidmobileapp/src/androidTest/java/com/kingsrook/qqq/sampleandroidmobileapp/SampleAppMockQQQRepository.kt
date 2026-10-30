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

package com.kingsrook.qqq.sampleandroidmobileapp

import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppNodeType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppSection
import com.kingsrook.qqq.frontend.android.core.model.metadata.QAppTreeNode
import com.kingsrook.qqq.frontend.android.core.model.metadata.QBrandingMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QProcessMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.FullyAnonymousAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionRequest
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionResponse
import com.kingsrook.qqq.frontend.android.core.model.processes.ProcessStepResult
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobComplete
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobStarted
import java.util.UUID

/***************************************************************************
 **
 ***************************************************************************/
class SampleAppMockQQQRepository : QQQRepository
{
   private var sessionUUID: String? = null
   private var environment: Environment = Environment("Mock Env", "https://mock/")

   val appMap = mapOf(
      "app1" to QAppMetaData(
         name = "app1", label = "App 1", sections = listOf(
            QAppSection("section1", "Section 1", processes = listOf("process1"))
         )
      )
   )

   val processMap = mapOf(
      "process1" to QProcessMetaData(name = "process1", label = "Process 1")
   )

   /***************************************************************************
    **
    ***************************************************************************/
   override fun setEnvironment(environment: Environment)
   {
      this.environment = environment
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override fun setSessionUUID(sessionUUID: String)
   {
      this.sessionUUID = sessionUUID
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun getAuthenticationMetaData(): BaseAuthenticationMetaData
   {
      return (FullyAnonymousAuthenticationMetaData("FULLY_ANONYMOUS"))
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun manageSession(body: ManageSessionRequest): ManageSessionResponse
   {
      return ManageSessionResponse(UUID.randomUUID().toString())
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun getMetaData(frontendName: String, frontendVersion: String, applicationName: String, applicationVersion: String): QInstance
   {
      if((sessionUUID ?: "").lowercase().contains("fail"))
      {
         throw RuntimeException("sessionUUID indicates that we should fail")
      }

      var appName = "Mock Application"
      if(environment.label == "Test Env 2")
      {
         appName += " 2"
      }

      val env1AppTree = listOf(
         QAppTreeNode(name = "app1", label = "App 1", type = QAppNodeType.APP),
         QAppTreeNode(name = "app2", label = "App 2", type = QAppNodeType.APP)
      )

      val env2AppTree = listOf(
         QAppTreeNode(name = "app3", label = "App 3", type = QAppNodeType.APP),
         QAppTreeNode(name = "app4", label = "App 4", type = QAppNodeType.APP)
      )

      return QInstance(
         branding = QBrandingMetaData(appName = appName),
         appTree = if(environment.label == "Test Env 2") env2AppTree else env1AppTree,
         apps = appMap,
         processes = processMap
      )
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun getProcessMetaData(processName: String): QProcessMetaData
   {
      val processMetaData = processMap[processName]
      if(processMetaData != null)
      {
         return processMetaData
      }

      return QProcessMetaData(name = processName, label = processName)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun processInit(processName: String, values: Map<String, Any?>, recordsParam: String?, recordIds: String?, filterJSON: String?, stepTimeoutMillis: Int?): ProcessStepResult
   {
      return QJobComplete(UUID.randomUUID().toString(), emptyMap(), "step2")
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun processStep(processName: String, processUUID: String, stepName: String, values: Map<String, Any?>, stepTimeoutMillis: Int?): ProcessStepResult
   {
      // return QJobComplete(UUID.randomUUID().toString(), emptyMap(), "step2")
      return QJobStarted(UUID.randomUUID().toString(), UUID.randomUUID().toString())
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun processJobStatus(processName: String, processUUID: String, jobUUID: String): ProcessStepResult
   {
      return QJobComplete(UUID.randomUUID().toString(), emptyMap(), "step2")
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun processCancel(processName: String, processUUID: String)
   {
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override fun getURI(path: String): String
   {
      TODO("Not yet implemented")
   }
}
