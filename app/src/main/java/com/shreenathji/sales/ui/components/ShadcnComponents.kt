package com.shreenathji.sales.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenathji.sales.ui.theme.*

enum class BadgeVariant {
    DEFAULT, SECONDARY, OUTLINE, SUCCESS, WARNING, INFO, INDIGO, ORANGE, PURPLE, DESTRUCTIVE
}

typealias ShadcnBadgeVariant = BadgeVariant

/**
 * Modern pill badge with rich vibrant status fills
 */
@Composable
fun ShadcnBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.SECONDARY
) {
    val (bgColor, textColor, borderColor) = when (variant) {
        BadgeVariant.DEFAULT -> Triple(Slate900, ShadcnWhite, null)
        BadgeVariant.SECONDARY -> Triple(Slate100, Slate700, Slate200)
        BadgeVariant.OUTLINE -> Triple(Color.Transparent, Slate700, Slate300)
        BadgeVariant.SUCCESS -> Triple(EmeraldSuccessBg, EmeraldSuccessText, EmeraldSuccessBorder)
        BadgeVariant.WARNING -> Triple(AmberWarningBg, AmberWarningText, AmberWarningBorder)
        BadgeVariant.INFO -> Triple(SkyInfoBg, SkyInfoText, SkyInfoBorder)
        BadgeVariant.INDIGO -> Triple(IndigoAccentBg, IndigoAccentText, IndigoAccentBorder)
        BadgeVariant.ORANGE -> Triple(BrandOrangeLight, BrandOrangeDark, BrandOrangeBorder)
        BadgeVariant.PURPLE -> Triple(PurpleAccentBg, PurpleAccentText, PurpleAccentBorder)
        BadgeVariant.DESTRUCTIVE -> Triple(RoseDestructiveBg, RoseDestructiveText, RoseDestructiveBorder)
    }

    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, shape)
                else Modifier
            )
            .background(bgColor, shape)
            .padding(horizontal = 9.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            letterSpacing = 0.2.sp
        )
    }
}

/**
 * Modern elevated card with subtle ambient border and shadow
 */
@Composable
fun ShadcnCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    backgroundColor: Color = ShadcnWhite,
    borderColor: Color = Slate200,
    elevation: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .shadow(elevation = elevation, shape = shape, spotColor = Slate900.copy(alpha = 0.08f))
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = shape,
        color = backgroundColor,
        shadowElevation = 0.dp
    ) {
        content()
    }
}

/**
 * Dynamic color-coded initial avatar box for party / store cards
 */
@Composable
fun PartyAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val colors = getAvatarColors(name)
    val initials = name.trim().split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { name.take(2).uppercase() }

    Surface(
        shape = shape,
        color = colors.bg,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                color = colors.text,
                fontSize = if (size > 40.dp) 15.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Modern high-impact primary button with bold typography
 */
@Composable
fun ShadcnPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
    containerColor: Color = Slate950,
    contentColor: Color = Color.White,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = Slate200,
            disabledContentColor = Slate400
        ),
        contentPadding = contentPadding,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 1.dp,
            pressedElevation = 3.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}

/**
 * Modern outlined button with crisp border and smooth touch feedback
 */
@Composable
fun ShadcnOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    content: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        border = BorderStroke(1.dp, Slate200),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ShadcnWhite,
            contentColor = Slate900
        ),
        contentPadding = contentPadding
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}

/**
 * Modern header with staff avatar badge and live indicators
 */
@Composable
fun ShadcnHeader(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null
) {
    Surface(
        color = ShadcnWhite,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (navigationIcon != null) {
                        navigationIcon()
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate950,
                            letterSpacing = (-0.3).sp
                        )
                        if (subtitle != null) {
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
                actions?.invoke()
            }
            HorizontalDivider(thickness = 1.dp, color = Slate200)
        }
    }
}

/**
 * Modern elevated search input field
 */
@Composable
fun ShadcnSearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search parties, markets, brokers...",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, fontSize = 13.sp, color = Slate400) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Slate400,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = Slate400,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        shape = RoundedCornerShape(14.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ShadcnWhite,
            unfocusedContainerColor = ShadcnWhite,
            focusedBorderColor = Slate900,
            unfocusedBorderColor = Slate200,
            focusedTextColor = Slate950,
            unfocusedTextColor = Slate950
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    )
}

/**
 * Daily Beat Progress Tracker Banner
 */
@Composable
fun BeatProgressTrackerCard(
    totalStops: Int,
    visitedStops: Int,
    modifier: Modifier = Modifier
) {
    if (totalStops == 0) return
    val progress = (visitedStops.toFloat() / totalStops.toFloat()).coerceIn(0f, 1f)
    val percentage = (progress * 100).toInt()

    ShadcnCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        borderColor = Slate200,
        backgroundColor = ShadcnWhite,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (percentage == 100) EmeraldSuccess else BrandOrange)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Today's Beat Progress",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (percentage == 100) EmeraldSuccessBg else Slate100,
                    border = BorderStroke(1.dp, if (percentage == 100) EmeraldSuccessBorder else Slate200)
                ) {
                    Text(
                        text = "$visitedStops of $totalStops Done ($percentage%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (percentage == 100) EmeraldSuccessText else Slate700,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Linear Progress Indicator
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (percentage == 100) EmeraldSuccess else BrandOrange,
                trackColor = Slate100
            )
        }
    }
}

