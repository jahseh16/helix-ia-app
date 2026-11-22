package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AppStroke

/** Logo oficial de Helix (mismo arte que el ícono del lanzador). */
@Composable
fun HelixLogo(size: Dp, modifier: Modifier = Modifier, corner: Dp = size * 0.3f) {
    val shape = RoundedCornerShape(corner)
    Image(
        painter = painterResource(R.drawable.helix_logo),
        contentDescription = "Helix",
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(shape).border(1.dp, AppStroke, shape)
    )
}
