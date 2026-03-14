package com.adaptix.client.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adaptix.client.ui.theme.*

// Glassmorphism card — frosted semi-transparent with gradient highlight on top edge
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val bg = SurfaceCard.copy(alpha = 0.65f)
    val border = Color.White.copy(alpha = 0.06f)
    val highlight = Color.White.copy(alpha = 0.03f)
    val shape = RoundedCornerShape(cornerRadius)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = shape,
        border = BorderStroke(0.5.dp, border),
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(highlight, bg, bg)
                    )
                )
                .fillMaxWidth()
        ) {
            content()
        }
    }
}

// Glass surface for elevated areas (top bars, bottom bars, headers)
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val bg = SurfaceCard.copy(alpha = 0.7f)
    val highlight = Color.White.copy(alpha = 0.04f)
    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        color = Color.Transparent,
        shape = shape,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.background(
                brush = Brush.verticalGradient(
                    colors = listOf(highlight, bg)
                )
            )
        ) {
            content()
        }
    }
}

// Glowing accent badge — small colored tag with glow effect
@Composable
fun GlowBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val glowColor = color.copy(alpha = 0.15f)
    val bgColor = color.copy(alpha = 0.10f)

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.20f)),
        modifier = modifier.drawBehind {
            drawRoundRect(
                color = glowColor,
                cornerRadius = CornerRadius(8.dp.toPx()),
                size = size.copy(
                    width = size.width + 2.dp.toPx(),
                    height = size.height + 2.dp.toPx()
                ),
                topLeft = Offset(-1.dp.toPx(), -1.dp.toPx())
            )
        }
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            androidx.compose.material3.Text(
                text,
                color = color,
                fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        }
    }
}

// Glass divider — subtle gradient line
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    val primary = Crimson
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        primary.copy(alpha = 0.15f),
                        primary.copy(alpha = 0.25f),
                        primary.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )
    )
}
