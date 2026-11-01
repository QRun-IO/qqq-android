/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.viewmodel

import com.kingsrook.qqq.frontend.android.core.model.metadata.FieldValidation
import com.kingsrook.qqq.frontend.android.core.model.metadata.QComponentType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendComponent
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendStepMetaData
import com.kingsrook.qqq.frontend.android.core.model.processes.ProcessMetaDataAdjustment
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobComplete
import com.kingsrook.qqq.frontend.android.core.model.widgets.WidgetBlock
import com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components.evaluateWidgetBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*******************************************************************************
 **
 *******************************************************************************/
class SemanticProcessTest {
   private val field = QFieldMetaData("barcode", "Customer barcode", type = QFieldType.STRING)
   private val other = QFieldMetaData("quantity", "Quantity", type = QFieldType.INTEGER)
   private val form = QFrontendComponent(QComponentType.EDIT_FORM)
   private val step = QFrontendStepMetaData("input", "Capture", components = listOf(form), formFields = listOf(field, other))

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun completionFollowsServerNextStepAndExplicitTerminalFlag() {
      assertFalse(SemanticProcess.terminal(QJobComplete("process", nextStep = "input")))
      assertFalse(SemanticProcess.terminal(QJobComplete("process", mapOf("noMoreSteps" to false), "input")))
      assertFalse(SemanticProcess.terminal(QJobComplete("process", mapOf("noMoreSteps" to "false"), "input")))
      assertTrue(SemanticProcess.terminal(QJobComplete("process", mapOf("noMoreSteps" to true), "input")))
      assertTrue(SemanticProcess.terminal(QJobComplete("process")))
      assertTrue(SemanticProcess.terminal(QJobComplete("process", mapOf("noMoreSteps" to false))))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun componentSubsetUsesFieldNamesAndServerOrder() {
      val component = form.copy(values = mapOf("includeFieldNames" to listOf("quantity")))
      assertEquals(listOf(other), SemanticProcess.fields(step, component))
      assertEquals(emptyList<QFieldMetaData>(), SemanticProcess.fields(step, form.copy(values = mapOf("includeFieldNames" to emptyList<String>()))))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun viewFormUsesViewFields() {
      assertEquals(listOf(other), SemanticProcess.fields(step.copy(viewFields = listOf(other)), QFrontendComponent(QComponentType.VIEW_FORM)))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun hiddenFieldAdjustmentsOmitFieldsFromFormsAndValidation() {
      val hidden = field.copy(isHidden = true, isRequired = true, type = QFieldType.UNKNOWN)
      val adjusted = SemanticProcess.adjust(
         listOf(step.copy(viewFields = listOf(field, other))),
         ProcessMetaDataAdjustment(updatedFields = mapOf(field.name to hidden))
      ).single()
      assertEquals(listOf(other), SemanticProcess.fields(adjusted, form))
      assertEquals(listOf(other), SemanticProcess.fields(adjusted, QFrontendComponent(QComponentType.VIEW_FORM)))
      assertNull("A hidden input must not prevent submitting the visible form", SemanticProcess.unsupported(adjusted, emptyMap(), null, emptySet()))
      assertTrue(SemanticProcess.fields(adjusted, form.copy(values = mapOf("includeFieldNames" to listOf(field.name)))).isEmpty())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun acknowledgmentMatchesOnlyTheExplicitExtensionResponseField() {
      val expected = ProcessAcknowledgment("acknowledgedEventId", "scan-1")
      assertFalse(expected.matches(emptyMap()))
      assertFalse(expected.matches(mapOf("acknowledgedEventId" to "scan-2")))
      assertFalse(expected.matches(mapOf("eventId" to "scan-1")))
      assertTrue(expected.matches(mapOf("acknowledgedEventId" to "scan-1")))
      assertTrue(ProcessAcknowledgment("savedRequest", "request-1").matches(mapOf("savedRequest" to "request-1")))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun completeReceiptRequiresSuccessAndANonemptyStringIdentity() {
      val policy = ProcessAcknowledgment("acknowledgedEventId", "scan-1", mapOf("scanSaved" to true), setOf("scanId"))
      val complete = mapOf("acknowledgedEventId" to "scan-1", "scanSaved" to true, "scanId" to "receipt")
      assertTrue(policy.matches(complete))
      for (name in complete.keys) assertFalse(policy.matches(complete - name))
      for (value in listOf(false, "true", null)) assertFalse(policy.matches(complete + ("scanSaved" to value)))
      for (value in listOf("", " ", 123, null)) assertFalse(policy.matches(complete + ("scanId" to value)))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun explicitNullDoesNotUseTheDefault() {
      val defaulted = field.copy(defaultValue = "default")
      assertEquals("default", semanticInitialValue(defaulted, emptyMap()))
      assertNull(semanticInitialValue(defaulted, mapOf(field.name to null)))
      assertEquals("entered", semanticInitialValue(defaulted, mapOf(field.name to "entered")))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun widgetInputMetadataUsesTypedSupportAndRejectsUnsupportedFields() {
      /*******************************************************************************
       **
       *******************************************************************************/
      fun screen(metadata: Map<String, Any?>) = step.copy(
         components = listOf(
            QFrontendComponent(
               QComponentType.WIDGET,
               mapOf("isAdHocWidget" to true, "blocks" to listOf(mapOf("blockType" to "INPUT_FIELD", "values" to mapOf("fieldMetaData" to metadata))))
            )
         )
      )

      val metadata = mapOf("name" to "quantity", "label" to "Quantity", "type" to "INTEGER", "isRequired" to true)
      assertNull(SemanticProcess.unsupported(screen(metadata), emptyMap(), null, emptySet()))
      assertEquals(QFieldType.INTEGER, SemanticProcess.widgetInputFields(screen(metadata), emptyMap()).single().type)
      for (type in listOf("FUTURE", "BLOB", "BOOLEAN")) assertNotNull(SemanticProcess.unsupported(screen(metadata + ("type" to type)), emptyMap(), null, emptySet()))
      assertNotNull(SemanticProcess.unsupported(screen(metadata - "type"), emptyMap(), null, emptySet()))
      assertNotNull(SemanticProcess.unsupported(screen(metadata + ("possibleValueSourceName" to "remote")), emptyMap(), null, emptySet()))
      assertNull(SemanticProcess.unsupported(screen(metadata + mapOf("isHidden" to true, "type" to "FUTURE")), emptyMap(), null, emptySet()))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun conditionalWidgetInputsMatchTheRenderedFields() {
      val input = mapOf(
         "blockType" to "INPUT_FIELD", "blockId" to "input", "values" to mapOf(
            "fieldMetaData" to mapOf("name" to "code", "label" to "Code", "type" to "STRING", "isRequired" to true)
         )
      )
      val composite = mapOf("blockType" to "COMPOSITE", "blockId" to "group", "conditional" to "showCode", "subBlocks" to listOf(input))
      val screen = step.copy(
         components = listOf(
            QFrontendComponent(
               QComponentType.WIDGET,
               mapOf("isAdHocWidget" to true, "blocks" to listOf(composite))
            )
         )
      )
      for (value in listOf(null, "", false, true, "yes")) {
         val values = mutableMapOf<String, Any?>("showCode" to value)
         val rendered = mutableListOf(WidgetBlock.fromMap(composite)!!)
         evaluateWidgetBlocks(rendered, values)
         val expectedCount = if (value == true || value == "yes") 1 else 0
         assertEquals(expectedCount, rendered.size)
         assertEquals(expectedCount, SemanticProcess.widgetInputFields(screen, values).size)
         val named = screen.copy(components = listOf(QFrontendComponent(QComponentType.WIDGET, mapOf("widgetName" to "named"))))
         assertEquals(expectedCount, SemanticProcess.widgetInputFields(named, values + ("named" to mapOf("subBlocks" to listOf(composite)))).size)
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun fieldChangesApplyWithoutReplacingStepList() {
      val updated = field.copy(label = "Updated barcode", isRequired = true)
      val result = SemanticProcess.adjust(listOf(step), ProcessMetaDataAdjustment(updatedFields = mapOf(field.name to updated)))
      assertEquals(updated, result.single().formFields!!.first())
      assertEquals(other, result.single().formFields!![1])
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun stepReplacementAndFieldUpdatesApplyTogether() {
      val replacement = step.copy(name = "replacement", label = "New server screen")
      val updated = field.copy(label = "New label")
      val result = SemanticProcess.adjust(listOf(step), ProcessMetaDataAdjustment(listOf(replacement), mapOf(field.name to updated)))
      assertEquals("replacement", result.single().name)
      assertEquals("New label", result.single().formFields!!.first().label)
      assertTrue(SemanticProcess.adjust(listOf(step), ProcessMetaDataAdjustment(updatedFrontendStepList = emptyList())).isEmpty())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun unknownAndUnsupportedComponentsCannotAdvance() {
      for (type in listOf(QComponentType.UNKNOWN, QComponentType.RECORD_LIST, QComponentType.BULK_EDIT_FORM))
         assertNotNull(SemanticProcess.unsupported(step.copy(components = listOf(QFrontendComponent(type))), emptyMap(), null, emptySet()))
      assertNull(SemanticProcess.unsupported(step, emptyMap(), null, emptySet()))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun customBlocksRequireRegistrationAndNestedUnknownBlocksBlockScreen() {
      /*******************************************************************************
       **
       *******************************************************************************/
      fun screen(blocks: List<Map<String, Any>>) = step.copy(
         components = listOf(
            QFrontendComponent(
               QComponentType.WIDGET,
               mapOf("isAdHocWidget" to true, "blocks" to blocks)
            )
         )
      )

      val scanner = screen(listOf(mapOf("blockType" to "SCANNER")))
      assertNotNull(SemanticProcess.unsupported(scanner, emptyMap(), null, emptySet()))
      assertNull(SemanticProcess.unsupported(scanner, emptyMap(), null, setOf("SCANNER")))
      val unknown = screen(listOf(mapOf("blockType" to "COMPOSITE", "subBlocks" to listOf(mapOf("blockType" to "FUTURE_CONTROL")))))
      assertNotNull(SemanticProcess.unsupported(unknown, emptyMap(), null, setOf("SCANNER")))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun remotePossibleValuesAndUnknownInputTypesAreExplicitlyUnavailable() {
      for (unsupportedField in listOf(field.copy(possibleValueSourceName = "remote"), field.copy(type = QFieldType.UNKNOWN)))
         assertNotNull(SemanticProcess.unsupported(step.copy(formFields = listOf(unsupportedField)), emptyMap(), null, emptySet()))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun requiredNumericAndLengthValidation() {
      assertNotNull(validateSemanticField(field.copy(isRequired = true), ""))
      assertNull(validateSemanticField(field, ""))
      assertNotNull(validateSemanticField(other, "x"))
      assertEquals(12, semanticFieldValue(other, "12"))
      assertNotNull(validateSemanticField(field.copy(validation = FieldValidation(maxLength = 3, tooLongBehavior = "ERROR")), "1234"))
      assertNull(validateSemanticField(field.copy(validation = FieldValidation(maxLength = 3, tooLongBehavior = "TRUNCATE")), "1234"))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun patternAndExactNumericBounds() {
      assertNotNull(validateSemanticField(field.copy(validation = FieldValidation(pattern = "[A-Z]+")), "ABC1"))
      assertNull(validateSemanticField(field.copy(validation = FieldValidation(pattern = "[A-Z]+")), "ABC"))
      val bounded = other.copy(validation = FieldValidation(minValue = "1", minInclusive = false, maxValue = "3"))
      assertNotNull(validateSemanticField(bounded, "1"))
      assertNull(validateSemanticField(bounded, "2"))
      assertNotNull(validateSemanticField(bounded, "4"))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun temporalInputsAreValidatedAsIsoValuesWithExplicitTimezone() {
      assertNull(validateSemanticField(field.copy(type = QFieldType.DATE), "2026-10-30"))
      assertNotNull(validateSemanticField(field.copy(type = QFieldType.DATE), "2026-02-30"))
      assertNull(validateSemanticField(field.copy(type = QFieldType.TIME), "09:30:00"))
      assertNotNull(validateSemanticField(field.copy(type = QFieldType.TIME), "25:30"))
      assertNull(validateSemanticField(field.copy(type = QFieldType.DATE_TIME), "2026-10-30T09:30:00-05:00"))
      assertNotNull(validateSemanticField(field.copy(type = QFieldType.DATE_TIME), "2026-10-30T09:30:00"))
   }
}
