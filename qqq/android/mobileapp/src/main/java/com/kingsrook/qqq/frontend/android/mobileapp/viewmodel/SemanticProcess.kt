/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.viewmodel

import com.kingsrook.qqq.frontend.android.core.model.metadata.QComponentType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendComponent
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendStepMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.processes.ProcessMetaDataAdjustment
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobComplete
import com.kingsrook.qqq.frontend.android.core.model.MapStringAny
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/*******************************************************************************
 ** Semantic decisions shared by the native UI and the submission boundary.
 *******************************************************************************/
internal object SemanticProcess {
   // A COMPLETE response omits nextStep when the process has finished; step-list position is irrelevant.
   /*******************************************************************************
    ** Use the server's next-step/completion contract rather than the position of a displayed step.
    *******************************************************************************/
   fun terminal(result: QJobComplete) = result.nextStep.isEmpty() || result.values["noMoreSteps"] == true

   /*******************************************************************************
    ** Select visible fields from the active step and the component's explicit inclusion list.
    *******************************************************************************/
   fun fields(step: QFrontendStepMetaData?, component: QFrontendComponent): List<QFieldMetaData> {
      val fields = if (component.type == QComponentType.VIEW_FORM) step?.viewFields else step?.formFields
      val included = component.values["includeFieldNames"] as? List<*>
      return fields.orEmpty().filter { !it.isHidden && (included == null || it.name in included) }
   }

   /*******************************************************************************
    ** Apply server metadata adjustments consistently across editable, read-only and record-list fields.
    *******************************************************************************/
   fun adjust(steps: List<QFrontendStepMetaData>, adjustment: ProcessMetaDataAdjustment?): List<QFrontendStepMetaData> {
      val updated = adjustment?.updatedFields.orEmpty()
      return (adjustment?.updatedFrontendStepList ?: steps).map { step ->
         step.copy(
            formFields = step.formFields?.map { updated[it.name] ?: it },
            viewFields = step.viewFields?.map { updated[it.name] ?: it },
            recordListFields = step.recordListFields?.map { updated[it.name] ?: it })
      }
   }

   /*******************************************************************************
    ** Stop submission when the current server screen requires a control this native client cannot represent.
    *******************************************************************************/
   fun unsupported(
      step: QFrontendStepMetaData?, values: Map<String, Any?>,
      instance: QInstance?, customBlocks: Set<String>
   ): String? {
      if (step == null) return "The server did not provide a supported current screen."
      for (component in step.components.orEmpty()) {
         when (component.type) {
            QComponentType.HELP_TEXT, QComponentType.HTML, QComponentType.PROCESS_SUMMARY_RESULTS, QComponentType.VIEW_FORM -> Unit
            QComponentType.EDIT_FORM -> {
               val unsupportedField = fields(step, component).firstOrNull {
                  it.type.name in setOf("BLOB", "UNKNOWN") ||
                     (it.possibleValueSourceName != null && it.inlinePossibleValueSource == null)
               }
               if (unsupportedField != null) return "${unsupportedField.label} requires an input control unavailable in this client."
            }

            QComponentType.WIDGET -> {
               val blocks = if (component.values["isAdHocWidget"] == true) component.values["blocks"] as? List<*>
               else {
                  val name = component.values["widgetName"] as? String ?: return "The server did not identify this widget."
                  if (instance?.widgets?.get(name)?.type != "composite") return "This widget is unavailable in this client."
                  (values[name] as? Map<*, *>)?.get("subBlocks") as? List<*>
               }
               if (blocks == null || !supportedBlocks(blocks, customBlocks)) return "This screen requires a widget control unavailable in this client."
            }

            else -> return "This screen requires ${component.type.name}, which is unavailable in this client."
         }
      }
      return null
   }

   /*******************************************************************************
    ** Validate every nested widget block and its input metadata before treating the screen as supported.
    *******************************************************************************/
   private fun supportedBlocks(blocks: List<*>, customBlocks: Set<String>): Boolean = blocks.all { raw ->
      val block = raw as? Map<*, *> ?: return@all false
      val type = block["blockType"] as? String ?: return@all false
      type in (setOf("AUDIO", "BUTTON", "COMPOSITE", "DIVIDER", "IMAGE", "INPUT_FIELD", "TEXT") + customBlocks) &&
         (type != "INPUT_FIELD" || inputField(block["values"] as? Map<*, *>)?.let { it.isHidden || supportedWidgetInput(it) } == true) &&
         ((block["subBlocks"] as? List<*>)?.let { supportedBlocks(it, customBlocks) } ?: true)
   }

   /*******************************************************************************
    ** INPUT_FIELD uses the same typed field metadata as forms; malformed metadata fails closed.
    *******************************************************************************/
   fun inputField(values: Map<*, *>?): QFieldMetaData? {
      val raw = values?.get("fieldMetaData") as? Map<*, *> ?: return null
      if (raw.keys.any { it !is String }) return null
      val fields = raw.entries.associate { it.key as String to it.value }
      return runCatching {
         Json { ignoreUnknownKeys = true }.decodeFromString<QFieldMetaData>(Json.encodeToString(MapStringAny(fields)))
      }.getOrNull()?.takeIf { it.name.isNotBlank() }
   }

   /*******************************************************************************
    ** Specialized selectors require EDIT_FORM until the widget vocabulary has a native control.
    *******************************************************************************/
   fun supportedWidgetInput(field: QFieldMetaData): Boolean = field.type !in setOf(QFieldType.UNKNOWN, QFieldType.BLOB, QFieldType.BOOLEAN) &&
      field.possibleValueSourceName == null && field.inlinePossibleValueSource.isNullOrEmpty()

   /*******************************************************************************
    ** Use the declared conditional value to decide widget visibility without guessing a business action.
    *******************************************************************************/
   fun widgetVisible(conditional: String?, values: Map<String, Any?>): Boolean = conditional == null ||
      values[conditional].let { it != null && it != "" && it != false }

   /*******************************************************************************
    ** Collect widget inputs at submission time as well as render time, so raw callbacks cannot bypass field rules.
    *******************************************************************************/
   fun widgetInputFields(step: QFrontendStepMetaData?, values: Map<String, Any?>): List<QFieldMetaData> {
      val fields = mutableListOf<QFieldMetaData>()

      /*******************************************************************************
       ** Collect only visible nested widget inputs so hidden controls cannot add unintended submission requirements.
       *******************************************************************************/
      fun visit(blocks: List<*>) {
         for (raw in blocks) {
            val block = raw as? Map<*, *> ?: continue
            if (!widgetVisible(block["conditional"] as? String, values)) continue
            if (block["blockType"] == "INPUT_FIELD") inputField(block["values"] as? Map<*, *>)?.let { fields.add(it) }
            (block["subBlocks"] as? List<*>)?.let(::visit)
         }
      }
      step?.components.orEmpty().filter { it.type == QComponentType.WIDGET }.forEach { component ->
         val blocks = if (component.values["isAdHocWidget"] == true) component.values["blocks"] as? List<*>
         else (component.values["widgetName"] as? String)?.let { (values[it] as? Map<*, *>)?.get("subBlocks") as? List<*> }
         blocks?.let(::visit)
      }
      return fields
   }
}
