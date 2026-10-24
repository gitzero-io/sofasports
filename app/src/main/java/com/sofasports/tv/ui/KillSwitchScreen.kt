package com.sofasports.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sofasports.tv.ui.theme.SofaColors

/** Shown instead of the whole app when remote config flips the kill switch. */
@Composable
fun KillSwitchScreen(message: String) {
    Column(
        Modifier.fillMaxSize().background(SofaColors.Background).padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sofa Sports", style = MaterialTheme.typography.displaySmall, color = SofaColors.TextPrimary)
        Text(
            message,
            style = MaterialTheme.typography.titleLarge,
            color = SofaColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp).widthIn(max = 800.dp),
        )
    }
}
