package org.elektronnet.mobile.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.elektronnet.mobile.wallet.AmountUnit

object UnitState {
    var current: AmountUnit by mutableStateOf(AmountUnit.Elek)
}
