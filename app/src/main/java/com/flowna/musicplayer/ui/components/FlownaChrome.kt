package com.flowna.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flowna.musicplayer.ui.theme.FlownaBorder
import com.flowna.musicplayer.ui.theme.FlownaSurface
import com.flowna.musicplayer.ui.theme.FlownaTextMuted
import com.flowna.musicplayer.ui.theme.FlownaTextPrimary
import com.flowna.musicplayer.ui.theme.FlownaTextSecondary
import com.flowna.musicplayer.ui.theme.Lavender100
import com.flowna.musicplayer.ui.theme.Lavender300
import com.flowna.musicplayer.ui.theme.Lavender600

private val FlownaPanelShape = RoundedCornerShape(18.dp)

@Composable
fun FlownaGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFBF8F4),
                    MaterialTheme.colorScheme.background,
                    Color(0xFFF1ECE6)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(180f, 140f),
                        radius = 760f
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(920f, 1380f),
                        radius = 880f
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.68f),
                            Color.Transparent
                        ),
                        center = Offset(520f, 240f),
                        radius = 540f
                    )
                )
        )
        content()
    }
}

@Composable
fun FlownaPanel(
    modifier: Modifier = Modifier,
    verticalSpacing: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = FlownaPanelShape,
        color = FlownaSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FlownaSurface)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content
        )
    }
}

@Composable
fun FlownaSectionHeading(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = FlownaTextPrimary
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = FlownaTextMuted
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = trailing
        )
    }
}

@Composable
fun FlownaCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    size: Dp = 42.dp,
    iconSize: Dp = 22.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (accent) {
            Lavender100
        } else {
            FlownaSurface
        },
        shadowElevation = 5.dp
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val scale = if (pressed) 0.94f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = if (accent) {
                    Lavender600
                } else {
                    FlownaTextPrimary
                }
            )
        }
    }
}

@Composable
fun FlownaSegmentedTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Lavender100,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }
                val itemPressed by interactionSource.collectIsPressedAsState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val scale = if (itemPressed && selected) 0.98f else 1f
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        FlownaSurface,
                                        FlownaSurface
                                    )
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.Transparent)
                                )
                            }
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onSelect(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            Lavender600
                        } else {
                            FlownaTextMuted
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FlownaStatusBadge(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    val containerColor = if (active) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.tertiary
    } else {
            MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(contentColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

@Composable
fun FlownaEqualizerDecoration(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val bars = listOf(10.dp, 16.dp, 22.dp, 14.dp, 28.dp, 18.dp, 12.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { height ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(height)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.7f))
            )
        }
    }
}
