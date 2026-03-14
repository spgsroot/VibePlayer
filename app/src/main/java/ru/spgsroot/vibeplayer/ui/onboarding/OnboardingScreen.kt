package ru.spgsroot.vibeplayer.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.ui.dialog.PasswordSetupDialog

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPrev() {
    MaterialTheme {
        OnboardingScreen(
            onComplete = { },
            onPasswordSet = {}
        )
    }
}

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onPasswordSet: (String) -> Unit
) {
    var showPasswordDialog by remember { mutableStateOf(false) }

    if (showPasswordDialog) {
        PasswordSetupDialog(
            onDismiss = { showPasswordDialog = false },
            onSetPassword = { password ->
                showPasswordDialog = false
                onPasswordSet(password)
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Верхний спейсер для центрирования контента
            Spacer(modifier = Modifier.weight(1f))

            // Заголовок
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Блок с шагами / фичами
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                FeatureItem(
                    text = stringResource(R.string.onboarding_step_1),
                    icon = Icons.Default.PlayArrow
                )
                FeatureItem(
                    text = stringResource(R.string.onboarding_step_2),
                    icon = Icons.Default.Settings
                )
                FeatureItem(
                    text = stringResource(R.string.onboarding_step_3),
                    icon = Icons.Default.CheckCircle
                )
            }

            // Нижний спейсер, чтобы прижать кнопки к низу экрана
            Spacer(modifier = Modifier.weight(1.5f))

            // Блок с кнопками
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { showPasswordDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp), // Удобная высота для нажатия
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.btn_set_password),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.btn_start),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// Вспомогательный компонент для красивого отображения элементов списка
@Composable
private fun FeatureItem(
    text: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
