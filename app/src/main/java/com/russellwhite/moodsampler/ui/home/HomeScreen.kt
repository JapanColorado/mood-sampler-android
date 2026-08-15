package com.russellwhite.moodsampler.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.russellwhite.moodsampler.BuildConfig
import com.russellwhite.moodsampler.appGraph
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Home (stub)")
        if (BuildConfig.DEBUG) {
            Button(onClick = {
                scope.launch { context.appGraph.promptScheduler.debugFireIn(10) }
            }) {
                Text("Fire test prompt in 10 s")
            }
        }
    }
}
