/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.viewmodel

import com.kingsrook.qqq.frontend.android.core.model.metadata.*
import com.kingsrook.qqq.frontend.android.core.model.processes.ProcessMetaDataAdjustment
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobComplete

/** Semantic decisions shared by the native UI and the submission boundary. */
internal object SemanticProcess
{
   // A COMPLETE response omits nextStep when the process has finished; step-list position is irrelevant.
   fun terminal(result: QJobComplete) = result.nextStep.isEmpty() || result.values["noMoreSteps"] == true

   fun fields(step: QFrontendStepMetaData?, component: QFrontendComponent): List<QFieldMetaData>
   {
      val fields = if(component.type == QComponentType.VIEW_FORM) step?.viewFields else step?.formFields
      val included = component.values["includeFieldNames"] as? List<*>
      return fields.orEmpty().filter { !it.isHidden && (included == null || it.name in included) }
   }

   fun adjust(steps: List<QFrontendStepMetaData>, adjustment: ProcessMetaDataAdjustment?): List<QFrontendStepMetaData>
   {
      val updated = adjustment?.updatedFields.orEmpty()
      return (adjustment?.updatedFrontendStepList ?: steps).map { step ->
         step.copy(formFields = step.formFields?.map { updated[it.name] ?: it },
            viewFields = step.viewFields?.map { updated[it.name] ?: it },
            recordListFields = step.recordListFields?.map { updated[it.name] ?: it })
      }
   }

   fun unsupported(step: QFrontendStepMetaData?, values: Map<String, Any?>,
      instance: QInstance?, customBlocks: Set<String>): String?
   {
      if(step == null) return "The server did not provide a supported current screen."
      for(component in step.components.orEmpty())
      {
         when(component.type)
         {
            QComponentType.HELP_TEXT, QComponentType.HTML, QComponentType.PROCESS_SUMMARY_RESULTS, QComponentType.VIEW_FORM -> Unit
            QComponentType.EDIT_FORM ->
            {
               val unsupportedField = fields(step, component).firstOrNull {
                  it.type.name in setOf("BLOB", "UNKNOWN") ||
                     (it.possibleValueSourceName != null && it.inlinePossibleValueSource == null)
               }
               if(unsupportedField != null) return "${unsupportedField.label} requires an input control unavailable in this client."
            }
            QComponentType.WIDGET ->
            {
               val blocks = if(component.values["isAdHocWidget"] == true) component.values["blocks"] as? List<*>
               else
               {
                  val name = component.values["widgetName"] as? String ?: return "The server did not identify this widget."
                  if(instance?.widgets?.get(name)?.type != "composite") return "This widget is unavailable in this client."
                  (values[name] as? Map<*, *>)?.get("subBlocks") as? List<*>
               }
               if(blocks == null || !supportedBlocks(blocks, customBlocks)) return "This screen requires a widget control unavailable in this client."
            }
            else -> return "This screen requires ${component.type.name}, which is unavailable in this client."
         }
      }
      return null
   }

   private fun supportedBlocks(blocks: List<*>, customBlocks: Set<String>): Boolean = blocks.all { raw ->
      val block = raw as? Map<*, *> ?: return@all false
      val type = block["blockType"] as? String ?: return@all false
      type in (setOf("AUDIO", "BUTTON", "COMPOSITE", "DIVIDER", "IMAGE", "INPUT_FIELD", "TEXT") + customBlocks) &&
         ((block["subBlocks"] as? List<*>)?.let { supportedBlocks(it, customBlocks) } ?: true)
   }
}
