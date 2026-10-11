package com.example.task_automator.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.task_automator.R

@Composable
fun SolvixLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.solvix_logo),
        contentDescription = "Solvix logo",
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
