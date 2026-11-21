package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppDanger
import com.example.ui.theme.MetaStyle

@Composable
fun ErrorBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = AppDanger.copy(alpha = 0.10f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AppDanger.copy(alpha = 0.30f))
    ) {
        Text(
            text = text,
            style = MetaStyle.copy(
                color = AppDanger,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}
