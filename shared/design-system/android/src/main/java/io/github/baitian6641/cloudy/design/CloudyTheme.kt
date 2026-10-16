package io.github.baitian6641.cloudy.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light = with(CloudyTokens.Light) {
    lightColorScheme(
        primary = Color(Primary), onPrimary = Color(OnPrimary),
        primaryContainer = Color(PrimaryContainer), onPrimaryContainer = Color(OnPrimaryContainer),
        secondary = Color(AccentContainer), onSecondary = Color(OnAccentContainer),
        secondaryContainer = Color(AccentContainer), onSecondaryContainer = Color(OnAccentContainer),
        background = Color(Canvas), onBackground = Color(TextPrimary),
        surface = Color(Surface), onSurface = Color(TextPrimary),
        surfaceVariant = Color(SurfaceMuted), onSurfaceVariant = Color(TextSecondary),
        surfaceContainer = Color(SurfaceRaised), outline = Color(Outline), outlineVariant = Color(Divider),
        error = Color(Danger), onError = Color(Surface),
        errorContainer = Color(DangerContainer), onErrorContainer = Color(Danger),
    )
}
private val Dark = with(CloudyTokens.Dark) {
    darkColorScheme(
        primary = Color(Primary), onPrimary = Color(OnPrimary),
        primaryContainer = Color(PrimaryContainer), onPrimaryContainer = Color(OnPrimaryContainer),
        secondary = Color(AccentContainer), onSecondary = Color(OnAccentContainer),
        secondaryContainer = Color(AccentContainer), onSecondaryContainer = Color(OnAccentContainer),
        background = Color(Canvas), onBackground = Color(TextPrimary),
        surface = Color(Surface), onSurface = Color(TextPrimary),
        surfaceVariant = Color(SurfaceMuted), onSurfaceVariant = Color(TextSecondary),
        surfaceContainer = Color(SurfaceRaised), outline = Color(Outline), outlineVariant = Color(Divider),
        error = Color(Danger), onError = Color(Canvas),
        errorContainer = Color(DangerContainer), onErrorContainer = Color(Danger),
    )
}

// System fonts remain intentional until font redistribution and fallback review.
private fun style(size: Int, height: Int, weight: Int) = TextStyle(
    fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = height.sp,
    fontWeight = FontWeight(weight),
)
private val CloudyTypography = with(CloudyTokens.Type) {
    Typography(
        headlineSmall = style(PageSize, PageLineheight, PageWeight),
        titleLarge = style(SectionSize, SectionLineheight, SectionWeight),
        bodyLarge = style(BodySize, BodyLineheight, BodyWeight),
        bodyMedium = style(BodySize, BodyLineheight, BodyWeight),
        labelLarge = style(LabelSize, LabelLineheight, LabelWeight),
        bodySmall = style(MetadataSize, MetadataLineheight, MetadataWeight),
    )
}

@Composable
fun CloudyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = CloudyTypography,
        shapes = Shapes(
            small = RoundedCornerShape(CloudyTokens.Radii.Field.dp),
            medium = RoundedCornerShape(CloudyTokens.Radii.Menu.dp),
            large = RoundedCornerShape(CloudyTokens.Radii.Dialog.dp),
        ),
        content = content,
    )
}
