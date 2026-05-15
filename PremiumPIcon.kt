package com.painite.keyboard.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.painite.keyboard.R

@Composable
@Suppress("UNUSED_PARAMETER")
fun PremiumPIcon(
    size: Dp = 48.dp,
    cornerRadius: Dp = size * 0.22f,
    fontSize: TextUnit = (size.value * 0.50f).sp
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Color(0xFF060B1A))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "Painite",
            modifier = Modifier.size(size * 1.28f),
            contentScale = ContentScale.Fit
        )
    }
}
