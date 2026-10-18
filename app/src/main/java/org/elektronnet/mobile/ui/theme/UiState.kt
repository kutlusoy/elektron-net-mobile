package org.elektronnet.mobile.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.elektronnet.mobile.ui.model.EffectSetting
import org.elektronnet.mobile.ui.model.UiSettings

object UiState {
    var settings: UiSettings by mutableStateOf(UiSettings())

    fun effectLevel(setting: EffectSetting): EffectLevel = when (setting) {
        EffectSetting.Off -> EffectLevel.Off
        EffectSetting.Subtle -> EffectLevel.Subtle
        EffectSetting.Full -> EffectLevel.Full
    }
}
