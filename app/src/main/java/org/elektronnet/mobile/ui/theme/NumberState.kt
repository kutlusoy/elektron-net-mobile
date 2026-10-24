package org.elektronnet.mobile.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import org.elektronnet.mobile.wallet.NumberStyle
import org.elektronnet.mobile.wallet.SeparatorChoice

object NumberState {
    var grouping by mutableStateOf(true)
    var groupChoice by mutableStateOf(SeparatorChoice.Default)
    var decimalChoice by mutableStateOf(SeparatorChoice.Default)

    // reads the state, so everything that shows an amount recomposes when a setting changes
    fun style(): NumberStyle = NumberStyle.forLocale(Locale.getDefault(), grouping, groupChoice, decimalChoice)
}
