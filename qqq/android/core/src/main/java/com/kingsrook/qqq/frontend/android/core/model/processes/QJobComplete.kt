package com.kingsrook.qqq.frontend.android.core.model.processes

import com.kingsrook.qqq.frontend.android.core.model.MapOfStringAny
import kotlinx.serialization.Serializable

/***************************************************************************
 **
 ***************************************************************************/
@Serializable
data class QJobComplete(
   override val processUUID: String,
   val values: MapOfStringAny = emptyMap(),
   val nextStep: String = "",
   val processMetaDataAdjustment: ProcessMetaDataAdjustment? = null,
) : ProcessStepResult()
