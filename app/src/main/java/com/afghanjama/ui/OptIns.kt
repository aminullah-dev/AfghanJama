package com.afghanjama.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3Experimental(content: @Composable () -> Unit) {
    content()
}
