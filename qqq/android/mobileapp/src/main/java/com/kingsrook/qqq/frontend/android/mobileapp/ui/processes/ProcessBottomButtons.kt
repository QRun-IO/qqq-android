package com.kingsrook.qqq.frontend.android.mobileapp.ui.processes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessViewModel

/***************************************************************************
 **
 ***************************************************************************/
@Composable
fun ProcessBottomButtons(processViewModel: ProcessViewModel)
{
   Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      modifier = Modifier.fillMaxWidth()
   )
   {
      val noMoreSteps = processViewModel.getNoMoreSteps()

      Button(onClick = processViewModel::cancel, enabled = !processViewModel.cancelling, modifier = Modifier.requiredWidth(100.dp))
      {
         Text(if(noMoreSteps) "Close" else "Cancel")
      }

      if(!noMoreSteps)
      {
         Button(onClick = { processViewModel.runStep() }, enabled = !processViewModel.waitingOnBackend && !processViewModel.cancelling && processViewModel.unsupportedReason() == null, modifier = Modifier.requiredWidth(100.dp))
         {
            Text(processViewModel.getNextButtonLabel())
         }
      }
   }
}
