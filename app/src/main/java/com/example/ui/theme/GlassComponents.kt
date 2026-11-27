package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassBackdrop(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit
) {
  Box(modifier = modifier.fillMaxSize()) {
    // Cache the static glacial-aurora backdrop into an offscreen GPU layer so scrolling never redraws it.
    Spacer(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
          val baseBrush = Brush.verticalGradient(
            colors = listOf(GlassCanvasTop, GlassCanvasMid, GlassCanvasBottom)
          )
          val maxDim = size.maxDimension
          val orb1Center = Offset(size.width * 0.12f, size.height * 0.09f)
          val orb1Radius = maxDim * 0.50f
          val orb1Brush = Brush.radialGradient(
            colors = listOf(GlassOrbAzure.copy(alpha = 0.44f), Color.Transparent),
            center = orb1Center,
            radius = orb1Radius
          )

          val orb2Center = Offset(size.width * 0.90f, size.height * 0.20f)
          val orb2Radius = maxDim * 0.48f
          val orb2Brush = Brush.radialGradient(
            colors = listOf(GlassOrbIndigo.copy(alpha = 0.36f), Color.Transparent),
            center = orb2Center,
            radius = orb2Radius
          )

          val orb3Center = Offset(size.width * 0.16f, size.height * 0.46f)
          val orb3Radius = maxDim * 0.40f
          val orb3Brush = Brush.radialGradient(
            colors = listOf(GlassOrbPrismPink.copy(alpha = 0.20f), Color.Transparent),
            center = orb3Center,
            radius = orb3Radius
          )

          val orb4Center = Offset(size.width * 0.86f, size.height * 0.58f)
          val orb4Radius = maxDim * 0.48f
          val orb4Brush = Brush.radialGradient(
            colors = listOf(GlassOrbMint.copy(alpha = 0.42f), Color.Transparent),
            center = orb4Center,
            radius = orb4Radius
          )

          val orb5Center = Offset(size.width * 0.22f, size.height * 0.88f)
          val orb5Radius = maxDim * 0.52f
          val orb5Brush = Brush.radialGradient(
            colors = listOf(GlassOrbSapphire.copy(alpha = 0.38f), Color.Transparent),
            center = orb5Center,
            radius = orb5Radius
          )

          val lightWave = Path().apply {
            moveTo(0f, size.height * 0.30f)
            cubicTo(
              size.width * 0.35f, size.height * 0.20f,
              size.width * 0.68f, size.height * 0.42f,
              size.width, size.height * 0.32f
            )
            lineTo(size.width, size.height * 0.56f)
            cubicTo(
              size.width * 0.65f, size.height * 0.66f,
              size.width * 0.30f, size.height * 0.46f,
              0f, size.height * 0.54f
            )
            close()
          }
          val waveBrush = Brush.linearGradient(
            colors = listOf(
              Color.White.copy(alpha = 0.24f),
              GlassOrbAzure.copy(alpha = 0.08f),
              Color.White.copy(alpha = 0.18f)
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height * 0.6f)
          )

          onDrawBehind {
            drawRect(brush = baseBrush)
            drawCircle(brush = orb1Brush, radius = orb1Radius, center = orb1Center)
            drawCircle(brush = orb2Brush, radius = orb2Radius, center = orb2Center)
            drawCircle(brush = orb3Brush, radius = orb3Radius, center = orb3Center)
            drawCircle(brush = orb4Brush, radius = orb4Radius, center = orb4Center)
            drawCircle(brush = orb5Brush, radius = orb5Radius, center = orb5Center)
            drawPath(path = lightWave, brush = waveBrush)
          }
        }
    )

    content()
  }
}

private val DefaultGlassRimBrush: Brush = Brush.linearGradient(
  colors = listOf(
    GlassRimHighlight,
    GlassRimPrism,
    GlassRimShadow
  )
)

private val DefaultAccentRimBrush: Brush = Brush.linearGradient(
  colors = listOf(
    Color.White.copy(alpha = 0.85f),
    Color.White.copy(alpha = 0.35f)
  )
)

fun glassRimBrush(): Brush = DefaultGlassRimBrush

fun Modifier.liquidGlassSurface(
  shape: Shape = RoundedCornerShape(24.dp),
  topColor: Color = GlassSurfaceBright,
  bottomColor: Color = GlassSurfaceSoft,
  tintColor: Color = Color.Transparent,
  elevation: Dp = 0.dp,
  borderWidth: Dp = 1.2.dp,
  borderBrush: Brush = DefaultGlassRimBrush
): Modifier {
  val base = if (elevation > 0.dp) {
    this.shadow(
      elevation = elevation,
      shape = shape,
      ambientColor = RailwayCobalt.copy(alpha = 0.18f),
      spotColor = NavyInk.copy(alpha = 0.12f)
    )
  } else {
    this
  }
  return base
    .clip(shape)
    .drawWithCache {
      val topSpec = GlassSheenTop.copy(alpha = 0.78f).compositeOver(tintColor)
      val upperBody = topColor.compositeOver(tintColor)
      val midBody = bottomColor.compositeOver(tintColor)
      val lowerEdge = topColor.copy(alpha = (topColor.alpha * 0.84f).coerceIn(0f, 1f)).compositeOver(tintColor)

      val glassBrush = Brush.verticalGradient(
        0.00f to topSpec,
        0.18f to upperBody,
        0.58f to midBody,
        0.88f to midBody,
        1.00f to lowerEdge
      )
      onDrawBehind {
        drawRect(brush = glassBrush)
      }
    }
    .border(
      width = borderWidth,
      brush = borderBrush,
      shape = shape
    )
}

fun Modifier.liquidGlassAccent(
  shape: Shape = RoundedCornerShape(16.dp),
  baseColor: Color = RailwayCobalt,
  highlightColor: Color = RailwayCobaltBright,
  elevation: Dp = 0.dp,
  borderWidth: Dp = 1.dp
): Modifier {
  val base = if (elevation > 0.dp) {
    this.shadow(
      elevation = elevation,
      shape = shape,
      ambientColor = baseColor.copy(alpha = 0.30f),
      spotColor = baseColor.copy(alpha = 0.22f)
    )
  } else {
    this
  }
  return base
    .clip(shape)
    .drawWithCache {
      val topSpec = Color.White.copy(alpha = 0.34f).compositeOver(highlightColor)
      val upper = highlightColor.copy(alpha = 0.94f)
      val mid = baseColor.copy(alpha = 0.92f)
      val accentBrush = Brush.verticalGradient(
        0.00f to topSpec,
        0.24f to upper,
        0.65f to mid,
        1.00f to baseColor
      )
      onDrawBehind {
        drawRect(brush = accentBrush)
      }
    }
    .border(
      width = borderWidth,
      brush = DefaultAccentRimBrush,
      shape = shape
    )
}

@Composable
fun GlassTile(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(24.dp),
  topColor: Color = GlassSurfaceBright,
  bottomColor: Color = GlassSurfaceSoft,
  tintColor: Color = Color.Transparent,
  elevation: Dp = 0.dp,
  borderBrush: Brush = DefaultGlassRimBrush,
  content: @Composable BoxScope.() -> Unit
) {
  Box(
    modifier = modifier.liquidGlassSurface(
      shape = shape,
      topColor = topColor,
      bottomColor = bottomColor,
      tintColor = tintColor,
      elevation = elevation,
      borderBrush = borderBrush
    ),
    content = content
  )
}

@Composable
fun GlassIconOrb(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(14.dp),
  tintColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
  content: @Composable BoxScope.() -> Unit
) {
  Box(
    modifier = modifier.liquidGlassSurface(
      shape = shape,
      topColor = GlassSurfaceBright,
      bottomColor = GlassSurfaceSoft,
      tintColor = tintColor,
      elevation = 0.dp,
      borderWidth = 1.dp
    ),
    contentAlignment = Alignment.Center,
    content = content
  )
}

@Composable
fun GlassPillBadge(
  text: String,
  tintColor: Color,
  textColor: Color,
  modifier: Modifier = Modifier,
  borderColor: Color = GlassRimHighlight,
  maxLines: Int = 2
) {
  val pillBorder = if (borderColor == GlassRimHighlight) {
    DefaultGlassRimBrush
  } else {
    Brush.linearGradient(listOf(GlassRimHighlight, borderColor.copy(alpha = 0.55f)))
  }
  Box(
    modifier = modifier
      .liquidGlassSurface(
        shape = RoundedCornerShape(16.dp),
        topColor = GlassSurfaceBright.copy(alpha = 0.75f),
        bottomColor = GlassSurfaceSoft,
        tintColor = tintColor,
        elevation = 0.dp,
        borderWidth = 1.dp,
        borderBrush = pillBorder
      )
      .padding(horizontal = 10.dp, vertical = 4.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = textColor,
      textAlign = TextAlign.Center,
      maxLines = maxLines,
      softWrap = maxLines > 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
fun glassCardColors(
  containerColor: Color = GlassSurfaceMid
): CardColors = CardDefaults.cardColors(
  containerColor = containerColor
)

fun glassCardBorder(): BorderStroke = BorderStroke(
  width = 1.2.dp,
  brush = DefaultGlassRimBrush
)
