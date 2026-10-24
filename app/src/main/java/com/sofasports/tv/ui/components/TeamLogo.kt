package com.sofasports.tv.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun TeamLogo(url: String?, contentDescription: String?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    AsyncImage(model = url, contentDescription = contentDescription, modifier = modifier.size(size))
}
