package ru.spgsroot.vibeplayer.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.spgsroot.vibeplayer.R

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.onboarding_step_1))
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_step_2))
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_step_3))
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onComplete) {
            Text(stringResource(R.string.btn_start))
        }
    }
}
