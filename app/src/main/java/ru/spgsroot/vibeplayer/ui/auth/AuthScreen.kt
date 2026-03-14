package ru.spgsroot.vibeplayer.ui.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.R // Замените на правильный путь к вашему R

private const val PIN_LENGTH = 4

@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    MaterialTheme {
        AuthScreen(
            onVerifyPassword = { it == "1234" },
            onAuthenticated = {}
        )
    }
}

@Composable
fun AuthScreen(
    onVerifyPassword: suspend (String) -> Boolean,
    onAuthenticated: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }

    // Анимация встряхивания и автоматическая очистка при ошибке
    LaunchedEffect(isError) {
        if (isError) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress) // Грубая вибрация при ошибке
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    20f at 50
                    -20f at 100
                    20f at 150
                    -20f at 200
                    10f at 250
                    -10f at 300
                    0f at 400
                }
            )
            delay(400) // Даем пользователю посмотреть на ошибку
            password = ""
            isError = false
        }
    }

    fun verifyAndLogin() {
        if (password.length != PIN_LENGTH || isLoading) return

        scope.launch {
            isLoading = true
            val success = onVerifyPassword(password)
            isLoading = false

            if (success) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // Успех
                onAuthenticated()
            } else {
                isError = true // Запустит LaunchedEffect с анимацией
            }
        }
    }

    fun appendDigit(digit: String) {
        if (password.length < PIN_LENGTH && !isLoading && !isError) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // Легкая отдача на клик
            password += digit

            if (password.length == PIN_LENGTH) {
                verifyAndLogin()
            }
        }
    }

    fun removeDigit() {
        if (password.isNotEmpty() && !isLoading && !isError) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            password = password.dropLast(1)
        }
    }

    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "back")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 699.dp),
            shape = RoundedCornerShape(32.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.enter_pin),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Контейнер для точек PIN с анимацией смещения (shake)
                Row(
                    modifier = Modifier.offset {
                        IntOffset(shakeOffset.value.dp.roundToPx(), 0)
                    },
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(PIN_LENGTH) { index ->
                        val filled = index < password.length

                        // Анимация цвета
                        val targetColor = when {
                            isError -> MaterialTheme.colorScheme.error
                            filled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        }
                        val animatedColor by animateColorAsState(
                            targetValue = targetColor,
                            animationSpec = tween(300),
                            label = "dotColor"
                        )

                        // Анимация размера (pop effect)
                        val animatedScale by animateFloatAsState(
                            targetValue = if (filled) 1.2f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "dotScale"
                        )

                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .scale(animatedScale)
                                .clip(CircleShape)
                                .background(animatedColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = when {
                        isLoading -> stringResource(R.string.verifying)
                        isError -> stringResource(R.string.invalid_pin)
                        password.isEmpty() -> stringResource(R.string.enter_4_digits)
                        else -> " "
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isError) FontWeight.Medium else FontWeight.Normal,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Разбиваем список кнопок на группы по 3
                    keys.chunked(3).forEach { rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            rowKeys.forEach { key ->
                                // Box с weight(1f) делит ширину на 3 равные части
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (key) {
                                        "" -> Box(modifier = Modifier.aspectRatio(1f))
                                        "back" -> KeyButton(
                                            onClick = { removeDigit() },
                                            enabled = password.isNotEmpty() && !isLoading && !isError,
                                            content = {
                                                Icon(
                                                    imageVector = Icons.Rounded.Backspace,
                                                    contentDescription = stringResource(R.string.delete),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        )
                                        else -> KeyButton(
                                            onClick = { appendDigit(key) },
                                            enabled = !isLoading && !isError,
                                            content = {
                                                Text(
                                                    text = key,
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun KeyButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    // Изменил на CircleShape для эстетичного круглого NumPad
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.aspectRatio(1f),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}
