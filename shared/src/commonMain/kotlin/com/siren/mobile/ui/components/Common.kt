package com.siren.mobile.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.Intensity
import com.siren.mobile.model.ResponseStatus
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenBlue
import com.siren.mobile.ui.theme.SirenGradients
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.tabular

enum class SurfaceTier {

    Primary,

    Secondary,

    Tertiary,
}

@Composable
fun SirenCard(
    modifier: Modifier = Modifier,
    tier: SurfaceTier = SurfaceTier.Secondary,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Space.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = if (tier == SurfaceTier.Primary) {
        RoundedCornerShape(Layout.cardLarge)
    } else {
        RoundedCornerShape(Layout.card)
    }
    val container = when (tier) {
        SurfaceTier.Primary -> MaterialTheme.colorScheme.surfaceContainerHigh
        SurfaceTier.Secondary -> MaterialTheme.colorScheme.surface
        SurfaceTier.Tertiary -> Color.Transparent
    }
    val border = if (tier == SurfaceTier.Secondary) {
        BorderStroke(Layout.hairline, MaterialTheme.colorScheme.outlineVariant)
    } else null

    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = container, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = container, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Layout.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Layout.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = Space.l + 42.dp + Space.m),
        thickness = Layout.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val body = @Composable {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Layout.rowMinHeight)
                .padding(horizontal = Space.l, vertical = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, color = Color.Transparent) { body() }
    } else {
        Box(modifier) { body() }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = Space.xs),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(title)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

enum class ButtonTone { Primary, Safe, Danger, Neutral, OnColor }

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    tone: ButtonTone = ButtonTone.Primary,
    onColorContent: Color = MaterialTheme.colorScheme.primary,
) {
    val status = SirenTheme.status
    val container: Color
    val content: Color
    when (tone) {

        ButtonTone.Primary -> {
            container = SirenBlue
            content = Color.White
        }
        ButtonTone.Safe -> {
            container = status.safeFill; content = Color.White
        }
        ButtonTone.Danger -> {
            container = status.dangerFill; content = Color.White
        }
        ButtonTone.Neutral -> {
            container = MaterialTheme.colorScheme.surfaceContainerHigh
            content = MaterialTheme.colorScheme.onSurface
        }

        ButtonTone.OnColor -> {
            container = Color.White; content = onColorContent
        }
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(Layout.buttonHeight),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(Layout.field),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,

            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
            icon?.let {
                Spacer(Modifier.width(Space.s))
                Icon(it, null, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(Layout.buttonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(Layout.field),
    ) {
        icon?.let {
            Icon(it, null, Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun Pill(
    text: String,
    fg: Color,
    bg: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(Layout.pill))
            .background(bg)
            .padding(horizontal = Space.s + Space.xxs, vertical = Space.xs + Space.xxs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, Modifier.size(12.dp), tint = fg) }
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusChip(status: ResponseStatus, modifier: Modifier = Modifier) {
    val s = SirenTheme.status
    val fg: Color
    val bg: Color
    val icon: ImageVector
    val label: String
    when (status) {
        ResponseStatus.SAFE -> {
            fg = s.safe; bg = s.safeContainer; icon = Icons.Filled.CheckCircle; label = "Safe"
        }
        ResponseStatus.NEEDS_HELP -> {
            fg = s.danger; bg = s.dangerContainer; icon = Icons.Filled.Sos; label = "Needs help"
        }
        ResponseStatus.NO_RESPONSE -> {
            fg = s.warn; bg = s.warnContainer; icon = Icons.Filled.HelpOutline; label = "No reply"
        }
    }
    Pill(label, fg, bg, modifier, icon)
}

@Composable
fun intensityColor(intensity: Intensity): Color {
    val s = SirenTheme.status
    return when (intensity) {
        Intensity.GREEN -> s.safeFill
        Intensity.YELLOW -> s.warnFill
        Intensity.RED -> s.dangerFill
    }
}

fun intensityBrush(intensity: Intensity): Brush = when (intensity) {
    Intensity.GREEN -> SirenGradients.safe
    Intensity.YELLOW -> SirenGradients.warn
    Intensity.RED -> SirenGradients.danger
}

@Composable
fun IntensityBadge(intensity: Intensity, modifier: Modifier = Modifier) {
    val c = intensityColor(intensity)
    Pill(
        text = "${intensity.severity.uppercase()} · LEVEL ${intensity.level}",
        fg = c,
        bg = c.copy(alpha = 0.14f),
        modifier = modifier,
    )
}

@Composable
fun Avatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    photo: String = "",
) {
    val bitmap = rememberPhotoBitmap(photo)
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(Layout.tile),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),

                contentScale = ContentScale.Crop,
            )
            return@Surface
        }
        Box(contentAlignment = Alignment.Center) {
            Text(
                initials,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun rememberPhotoBitmap(photo: String): ImageBitmap? = remember(photo) {
    if (photo.isBlank()) return@remember null
    runCatching { Base64.decode(photo).decodeToImageBitmap() }.getOrNull()
}

@Composable
fun RosterBreakdown(
    safe: Int,
    needsHelp: Int,
    noReply: Int,
    modifier: Modifier = Modifier,
) {
    val s = SirenTheme.status
    val total = safe + needsHelp + noReply

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Layout.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Layout.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Class status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (total == 1) "1 student" else "$total students",
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(Layout.pill)),
            ) {
                if (total == 0) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                } else {

                    if (needsHelp > 0) {
                        Box(Modifier.weight(needsHelp.toFloat()).fillMaxHeight().background(s.dangerFill))
                    }
                    if (noReply > 0) {
                        Box(Modifier.weight(noReply.toFloat()).fillMaxHeight().background(s.warnFill))
                    }
                    if (safe > 0) {
                        Box(Modifier.weight(safe.toFloat()).fillMaxHeight().background(s.safeFill))
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LegendEntry(needsHelp, "Needs help", s.dangerFill)
                LegendEntry(noReply, "No reply", s.warnFill)
                LegendEntry(safe, "Safe", s.safeFill)
            }
        }
    }
}

@Composable
private fun LegendEntry(count: Int, label: String, dot: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Text(
            "$count",
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

enum class BannerTone { Info, Warn, Danger, Neutral }

@Composable
fun InfoBanner(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
) {
    val s = SirenTheme.status
    val fg: Color
    val bg: Color
    when (tone) {
        BannerTone.Info -> {
            fg = MaterialTheme.colorScheme.onPrimaryContainer
            bg = MaterialTheme.colorScheme.primaryContainer
        }
        BannerTone.Warn -> { fg = s.onWarnContainer; bg = s.warnContainer }
        BannerTone.Danger -> { fg = s.onDangerContainer; bg = s.dangerContainer }
        BannerTone.Neutral -> {
            fg = MaterialTheme.colorScheme.onSurfaceVariant
            bg = MaterialTheme.colorScheme.surfaceContainer
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Layout.field))
            .background(bg)
            .padding(Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = fg)
        Text(text, style = MaterialTheme.typography.bodySmall, color = fg)
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    InfoBanner(
        text = "You're offline. Your response is saved on this device and sent automatically once you reconnect.",
        icon = Icons.Filled.CloudOff,
        modifier = modifier,
        tone = BannerTone.Neutral,
    )
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxxl, horizontal = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ErrorState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxl, horizontal = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Icon(Icons.Filled.CloudOff, null, Modifier.size(40.dp), tint = SirenTheme.status.danger)
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        onRetry?.let { SecondaryButton("Try again", it, Modifier.fillMaxWidth(0.7f), icon = Icons.Filled.Refresh) }
    }
}

@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 6.dp,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmer",
    )
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha))
            .clearAndSetSemantics { },
    )
}

@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(Space.l),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Skeleton(Modifier.size(42.dp), height = 42.dp, cornerRadius = Layout.tile)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Skeleton(Modifier.fillMaxWidth(0.55f))
            Skeleton(Modifier.fillMaxWidth(0.35f), height = 12.dp)
        }
    }
}

@Composable
fun SkeletonList(rows: Int = 4, modifier: Modifier = Modifier) {
    ListGroup(modifier) {
        repeat(rows) { i ->
            SkeletonRow()
            if (i < rows - 1) RowDivider()
        }
    }
}

@Composable
fun SirenField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        shape = RoundedCornerShape(Layout.field),
        singleLine = singleLine,
        isError = isError,
        leadingIcon = leadingIcon?.let { { Icon(it, null) } },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        if (revealed) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (revealed) "Hide password" else "Show password",
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation()
        else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
        ),
        supportingText = supportingText?.let { { Text(it) } },
        colors = OutlinedTextFieldDefaults.colors(),
    )
}
