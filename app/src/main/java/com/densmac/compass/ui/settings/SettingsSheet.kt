package com.densmac.compass.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.densmac.compass.R
import com.densmac.compass.domain.CoordinateFormat
import com.densmac.compass.domain.ElevationUnit
import com.densmac.compass.domain.HapticStrength
import com.densmac.compass.domain.NorthPreference
import com.densmac.compass.domain.Settings
import com.densmac.compass.domain.ThemeMode
import com.densmac.compass.ui.theme.Inter
import com.densmac.compass.ui.theme.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(settings: Settings, update: ((Settings) -> Settings) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Bar,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Choice(
                stringResource(R.string.setting_theme),
                listOf(ThemeMode.DARK to R.string.theme_dark, ThemeMode.LIGHT to R.string.theme_light),
                settings.theme,
            ) { v -> update { it.copy(theme = v) } }
            Choice(
                stringResource(R.string.setting_north),
                listOf(NorthPreference.TRUE to R.string.north_true, NorthPreference.MAGNETIC to R.string.north_magnetic),
                settings.north,
            ) { v -> update { it.copy(north = v) } }
            Toggle(stringResource(R.string.setting_haptics), settings.hapticsEnabled) { v ->
                update { it.copy(hapticsEnabled = v) }
            }
            if (settings.hapticsEnabled) {
                Choice(
                    stringResource(R.string.setting_haptic_strength),
                    listOf(HapticStrength.SUBTLE to R.string.haptic_subtle, HapticStrength.NORMAL to R.string.haptic_normal),
                    settings.hapticStrength,
                ) { v -> update { it.copy(hapticStrength = v) } }
            }
            Toggle(stringResource(R.string.setting_sound), settings.soundEnabled) { v ->
                update { it.copy(soundEnabled = v) }
            }
            Choice(
                stringResource(R.string.setting_units),
                listOf(ElevationUnit.METRES to R.string.units_m, ElevationUnit.FEET to R.string.units_ft),
                settings.elevationUnit,
            ) { v -> update { it.copy(elevationUnit = v) } }
            Choice(
                stringResource(R.string.setting_coords),
                listOf(CoordinateFormat.DMS to R.string.coords_dms, CoordinateFormat.DECIMAL to R.string.coords_decimal),
                settings.coordinateFormat,
            ) { v -> update { it.copy(coordinateFormat = v) } }
            Toggle(stringResource(R.string.setting_keep_screen), settings.keepScreenOn) { v ->
                update { it.copy(keepScreenOn = v) }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Palette.Text, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp)
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(label)
        Switch(
            checked,
            onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = Palette.Accent,
                uncheckedThumbColor = Palette.Secondary,
                uncheckedTrackColor = Palette.Divider,
                uncheckedBorderColor = Palette.Divider,
            ),
        )
    }
}

@Composable
private fun <T> Choice(label: String, options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(label)
        Row(Modifier.clip(RoundedCornerShape(50)).background(Palette.Divider)) {
            options.forEach { (value, name) ->
                val active = value == selected
                Text(
                    stringResource(name),
                    color = if (active) androidx.compose.ui.graphics.Color.White else Palette.Secondary,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Palette.Accent else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}
