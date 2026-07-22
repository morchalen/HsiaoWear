package com.example.hsiaowear.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hsiaowear.R
import com.example.hsiaowear.ui.theme.LocalAppShape
import com.example.hsiaowear.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

sealed class OnboardingStep {
    object Welcome : OnboardingStep()
    object ApiConfig : OnboardingStep()
    object Features : OnboardingStep()
}

@Composable
fun OnboardingScreen(
    viewModel: SettingsViewModel,
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableStateOf<OnboardingStep>(OnboardingStep.Welcome) }
    var apiEndpoint by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    val apiSettings by viewModel.apiSettings.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(apiSettings) {
        apiEndpoint = apiSettings.baseUrl
        apiKey = apiSettings.apiKey
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (currentStep) {
                OnboardingStep.Welcome -> WelcomeStep {
                    currentStep = OnboardingStep.ApiConfig
                }

                OnboardingStep.ApiConfig -> ApiConfigStep(
                    apiEndpoint = apiEndpoint,
                    onEndpointChange = { apiEndpoint = it },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    onTestConnection = { viewModel.testApiConnection(apiEndpoint, apiKey) },
                    onNext = {
                        viewModel.updateApiEndpoint(apiEndpoint)
                        scope.launch { viewModel.markOnboardingCompleted() }
                        currentStep = OnboardingStep.Features
                    },
                    onSkip = {
                        scope.launch { viewModel.markOnboardingCompleted() }
                        currentStep = OnboardingStep.Features
                    }
                )

                OnboardingStep.Features -> FeaturesStep(onComplete = onComplete)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            StepIndicator(isActive = currentStep is OnboardingStep.Welcome)
            Spacer(modifier = Modifier.width(8.dp))
            StepIndicator(isActive = currentStep is OnboardingStep.ApiConfig)
            Spacer(modifier = Modifier.width(8.dp))
            StepIndicator(isActive = currentStep is OnboardingStep.Features)
        }
    }
}

@Composable
private fun StepIndicator(isActive: Boolean) {
    Box(
        modifier = Modifier
            .size(if (isActive) 8.dp else 6.dp)
            .background(
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
    )
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val shapes = LocalAppShape.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)
    ) {
        Text(
            text = "👗",
            fontSize = 40.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.button,
            contentPadding = ButtonContentPadding,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = stringResource(R.string.onboarding_next),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ApiConfigStep(
    apiEndpoint: String,
    onEndpointChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val shapes = LocalAppShape.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)
    ) {
        Text(
            text = "🔗",
            fontSize = 40.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_api_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = apiEndpoint,
            onValueChange = onEndpointChange,
            label = { Text(stringResource(R.string.onboarding_api_hint), style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.input,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("https://api.example.com/", style = MaterialTheme.typography.bodyLarge) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key", style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.input,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("sk-xxxxxxxxxxxx", style = MaterialTheme.typography.bodyLarge) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_api_hint_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onTestConnection,
                modifier = Modifier.weight(1f),
                shape = shapes.button,
                contentPadding = ButtonContentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = stringResource(R.string.onboarding_api_test),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
                shape = shapes.button,
                contentPadding = ButtonContentPadding,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_next),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onSkip) {
            Text(
                text = stringResource(R.string.onboarding_api_skip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FeaturesStep(onComplete: () -> Unit) {
    val shapes = LocalAppShape.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)
    ) {
        Text(
            text = "✨",
            fontSize = 40.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_features),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                FeatureItem("🧥", stringResource(R.string.onboarding_feature_wardrobe))
                Spacer(modifier = Modifier.height(16.dp))
                FeatureItem("🤖", stringResource(R.string.onboarding_feature_ai))
                Spacer(modifier = Modifier.height(16.dp))
                FeatureItem("👔", stringResource(R.string.onboarding_feature_recommend))
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.button,
            contentPadding = ButtonContentPadding,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = stringResource(R.string.onboarding_start),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FeatureItem(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = icon, fontSize = 24.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private val ButtonContentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp)
