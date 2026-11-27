package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LiquidGlassLightScheme = lightColorScheme(
  primary = RailwayCobalt,
  onPrimary = Color.White,
  primaryContainer = RailwayCobaltContainer,
  onPrimaryContainer = RailwayCobaltOnContainer,
  secondary = DepotTeal,
  onSecondary = Color.White,
  secondaryContainer = DepotTealContainer,
  onSecondaryContainer = DepotTealOnContainer,
  tertiary = SafetyAmber,
  onTertiary = Color.White,
  tertiaryContainer = SafetyAmberContainer,
  onTertiaryContainer = SafetyAmberOnContainer,
  background = GlassCanvasTop,
  onBackground = NavyInk,
  surface = GlassSurfaceBright,
  onSurface = NavyInk,
  surfaceVariant = GlassSurfaceSoft,
  onSurfaceVariant = SteelSecondaryInk,
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = GlassPopupSurface,
  surfaceContainer = GlassPopupSurface,
  surfaceContainerHigh = GlassPopupSurface,
  surfaceContainerHighest = GlassPopupSurface,
  outline = GlassRimShadow,
  outlineVariant = GlassRimPrism,
  error = SignalCrimson,
  onError = Color.White,
  errorContainer = SignalCrimsonContainer,
  onErrorContainer = SignalCrimsonOnContainer
)

val AppShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(18.dp),
  large = RoundedCornerShape(24.dp)
)

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = LiquidGlassLightScheme,
    typography = Typography,
    shapes = AppShapes,
    content = content
  )
}
