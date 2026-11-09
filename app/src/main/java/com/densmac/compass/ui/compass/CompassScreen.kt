package com.densmac.compass.ui.compass

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.densmac.compass.R
import com.densmac.compass.domain.NO_VALUE
import com.densmac.compass.domain.NorthReference
import com.densmac.compass.domain.SensorWarning
import com.densmac.compass.domain.cardinalOf
import com.densmac.compass.ui.theme.Inter
import com.densmac.compass.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition

private fun hasLocation(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
fun CompassScreen(viewModel: CompassViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Kept as a State object: only the animation loop reads it, so this screen doesn't recompose.
    val frames = viewModel.frames.collectAsStateWithLifecycle()
    val motion = remember { CompassMotion() }
    var askedBefore by rememberSaveable { mutableStateOf(false) }
    var showCalibration by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.onLocationPermission(hasLocation(context))
        viewModel.setAudible(true)
        onPauseOrDispose { viewModel.setAudible(false) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        askedBefore = true
        viewModel.onLocationPermission(hasLocation(context))
    }
    val requestLocation = {
        val activity = context as? Activity
        val blocked = askedBefore && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        if (blocked) {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        } else {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    if (!state.hasCompass) {
        Message(stringResource(R.string.no_compass_title), stringResource(R.string.no_compass_body))
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val w = maxWidth
        val h = maxHeight
        if (w > h) {
            val dial = min(h * 0.92f, w * 0.48f)
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                CompassDial(frames, motion, viewModel, Modifier.size(dial))
                Spacer(Modifier.weight(0.2f))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Readout(state, motion, w * 0.5f) { showCalibration = true }
                    Spacer(Modifier.height(24.dp))
                    InfoTable(state, requestLocation, w * 0.42f)
                }
            }
        } else {
            val reserved = 56.dp + 150.dp + 186.dp
            val dial = min(w * 0.825f, (h - reserved).coerceAtLeast(160.dp)).coerceAtMost(520.dp)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(56.dp))
                Spacer(Modifier.weight(0.45f))
                Readout(state, motion, w) { showCalibration = true }
                Spacer(Modifier.weight(1f))
                CompassDial(frames, motion, viewModel, Modifier.size(dial))
                Spacer(Modifier.weight(1f))
                InfoTable(state, requestLocation, w)
                Spacer(Modifier.weight(0.6f))
            }
        }
    }

    if (showCalibration) CalibrationSheet { showCalibration = false }
}

private fun Dp.coerceAtLeast(other: Dp): Dp = if (this < other) other else this
private fun Dp.coerceAtMost(other: Dp): Dp = if (this > other) other else this

/** Instrument type: sized from the layout, so it ignores the system font scale. */
@Composable
private fun Dp.fixedSp(): TextUnit = with(LocalDensity.current) { this@fixedSp.toSp() }

@Composable
private fun Readout(state: CompassUiState, motion: CompassMotion, width: Dp, onWarning: () -> Unit) {
    // The readout shows what the wheel shows, spinning or not.
    val shown = motion.displayDegree
    val degree = shown ?: state.degree
    val cardinal = if (shown != null) cardinalOf(shown.toFloat()) else state.cardinal
    val isNorth = if (shown != null) motion.displayNorth else state.isNorth
    val color by animateColorAsState(
        if (isNorth) Palette.Accent else Palette.Text,
        tween(150),
        label = "readout",
    )
    val big = (width * 0.0955f).fixedSp()
    val small = (width * 0.078f).fixedSp()
    val text: AnnotatedString = remember(state.hasHeading, degree, cardinal, big, small) {
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = big, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")) {
                append(if (state.hasHeading) degree.toString() else NO_VALUE)
                append("°")
            }
            if (state.hasHeading) {
                withStyle(SpanStyle(fontSize = small, fontWeight = FontWeight.Bold)) { append(cardinal) }
            }
        }
    }
    val description = stringResource(R.string.heading_desc, degree, cardinal)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text,
            color = color.copy(alpha = if (state.unstable) 0.45f else 1f),
            style = TextStyle(fontFamily = Inter),
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                when (state.reference) {
                    NorthReference.TRUE -> R.string.ref_true
                    NorthReference.TRUE_APPROX -> R.string.ref_true_approx
                    NorthReference.MAGNETIC -> R.string.ref_magnetic
                },
            ),
            color = Palette.Secondary,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
        )
        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            if (state.warning != SensorWarning.NONE) {
                Text(
                    stringResource(
                        if (state.warning == SensorWarning.CALIBRATE) R.string.calibrate_pill else R.string.interference_pill,
                    ),
                    color = Palette.Accent,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Palette.Bar)
                        .clickable(onClick = onWarning)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoTable(state: CompassUiState, requestLocation: () -> Unit, width: Dp) {
    val info = state.info
    val size = (min(width, 480.dp) * 0.048f).fixedSp()
    val latitude = if (state.locationGranted) info.latitude else stringResource(R.string.enable_location)
    Column(Modifier.fillMaxWidth().padding(horizontal = min(width, 480.dp) * 0.11f)) {
        InfoRow(
            info.latitudeLabel,
            latitude,
            size,
            valueIsAction = !state.locationGranted,
            onClick = if (state.locationGranted) null else requestLocation,
        )
        HorizontalDivider(color = Palette.Divider, thickness = 0.5.dp)
        InfoRow(info.longitudeLabel, info.longitude, size)
        HorizontalDivider(color = Palette.Divider, thickness = 0.5.dp)
        InfoRow(stringResource(R.string.elevation), info.elevation, size)
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    size: TextUnit,
    valueIsAction: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.Secondary, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = size)
        Text(
            value,
            color = if (valueIsAction) Palette.Accent else Palette.Text,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
            fontSize = size,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Palette.Text, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(10.dp))
        Text(body, color = Palette.Secondary, fontFamily = Inter, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Bar) {
        Column(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.calibrate_title),
                color = Palette.Text,
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(20.dp))
            FigureEight()
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.calibrate_body),
                color = Palette.Secondary,
                fontFamily = Inter,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FigureEight() {
    val colors = Palette
    val transition = rememberInfiniteTransition(label = "figure8")
    val progress by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "progress",
    )
    Canvas(Modifier.size(width = 200.dp, height = 90.dp)) {
        val a = size.width * 0.42f
        val b = size.height * 0.8f
        fun point(t: Float) = Offset(
            center.x + a * sin(t),
            center.y + b * sin(t) * cos(t),
        )
        val path = Path().apply {
            val start = point(0f)
            moveTo(start.x, start.y)
            for (i in 1..120) {
                val p = point(i / 120f * 2f * PI.toFloat())
                lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(path, colors.Divider, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(colors.Accent, 6.dp.toPx(), point(progress * 2f * PI.toFloat()))
    }
}
