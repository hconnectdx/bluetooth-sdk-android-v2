package kr.co.hconnect.bluetooth_sdk_android_peripheral_example.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val WearColorPalette = Colors(
    primary = Color(0xFF6495ED),
    primaryVariant = Color(0xFF4177CC),
    onPrimary = Color.White,
    secondary = Color(0xFF6495ED),
    onSecondary = Color.White,
    background = Color(0xFF121212),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF1F1F1),
    error = Color(0xFFCF6679),
    onError = Color.Black
)

@Composable
fun PeripheralExampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearColorPalette,
        content = content
    )
}
