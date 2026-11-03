package com.example.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppSettings
import com.example.data.model.KeyVerificationState

private const val OLLAMA_TOKEN_PORTAL_URL = "https://ollama.com/settings/keys"
private const val GEMINI_TOKEN_PORTAL_URL = "https://aistudio.google.com/app/apikey"
private const val DEEPL_TOKEN_PORTAL_URL = "https://www.deepl.com/your-account/keys"

@Composable
fun ApiKeysSettingsPage(
    settings: AppSettings,
    ollamaKeyStatus: KeyVerificationState,
    geminiKeyStatus: KeyVerificationState,
    deeplKeyStatus: KeyVerificationState,
    onVerifyOllamaKey: (String) -> Unit,
    onVerifyGeminiKey: (String) -> Unit,
    onVerifyDeeplKey: (String) -> Unit,
    onSaveOrReplaceKey: (providerCode: String, newValue: String) -> Unit,
    onDeleteKey: (providerCode: String) -> Unit,
    onBack: () -> Unit
) {
    SettingsSubPageScaffold(
        title = stringResource(R.string.settings_api_keys),
        onBack = onBack
    ) {
        // 1. OLLAMA
        SingleApiKeyManagementCard(
            title = stringResource(R.string.api_key_ollama_title),
            fieldLabel = stringResource(R.string.settings_ollama_key),
            providerTag = "ollama",
            storedKeyOverride = settings.ollamaApiKeyOverride,
            effectiveKeyPresent = settings.effectiveOllamaKey.isNotEmpty(),
            portalUrl = OLLAMA_TOKEN_PORTAL_URL,
            verificationState = ollamaKeyStatus,
            inputTestTag = "input_ollama_api_key",
            onVerify = onVerifyOllamaKey,
            onSaveOrReplace = { onSaveOrReplaceKey("OLLAMA", it) },
            onDelete = { onDeleteKey("OLLAMA") }
        )

        // 2. GEMINI
        SingleApiKeyManagementCard(
            title = stringResource(R.string.api_key_gemini_title),
            fieldLabel = stringResource(R.string.settings_gemini_key),
            providerTag = "gemini",
            storedKeyOverride = settings.geminiApiKeyOverride,
            effectiveKeyPresent = settings.effectiveGeminiKey.isNotEmpty(),
            portalUrl = GEMINI_TOKEN_PORTAL_URL,
            verificationState = geminiKeyStatus,
            inputTestTag = "input_gemini_api_key",
            onVerify = onVerifyGeminiKey,
            onSaveOrReplace = { onSaveOrReplaceKey("GEMINI", it) },
            onDelete = { onDeleteKey("GEMINI") }
        )

        // 3. DEEPL
        SingleApiKeyManagementCard(
            title = stringResource(R.string.api_key_deepl_title),
            fieldLabel = stringResource(R.string.settings_deepl_key),
            providerTag = "deepl",
            storedKeyOverride = settings.deeplApiKeyOverride,
            effectiveKeyPresent = settings.effectiveDeeplKey.isNotEmpty(),
            portalUrl = DEEPL_TOKEN_PORTAL_URL,
            verificationState = deeplKeyStatus,
            inputTestTag = "input_deepl_api_key",
            onVerify = onVerifyDeeplKey,
            onSaveOrReplace = { onSaveOrReplaceKey("DEEPL", it) },
            onDelete = { onDeleteKey("DEEPL") }
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.security_prototype_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

@Composable
private fun SingleApiKeyManagementCard(
    title: String,
    fieldLabel: String,
    providerTag: String,
    storedKeyOverride: String,
    effectiveKeyPresent: Boolean,
    portalUrl: String,
    verificationState: KeyVerificationState,
    inputTestTag: String,
    onVerify: (String) -> Unit,
    onSaveOrReplace: (String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var inputValue by remember(storedKeyOverride) { mutableStateOf(storedKeyOverride) }
    var isSecretVisible by rememberSaveable { mutableStateOf(false) }

    val isVerifying = verificationState is KeyVerificationState.Verifying

    SettingsSectionCard(
        title = title,
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        OutlinedTextField(
            value = inputValue,
            onValueChange = { inputValue = it },
            label = { Text(fieldLabel) },
            singleLine = true,
            visualTransformation = if (isSecretVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 2.dp)
                ) {
                    IconButton(
                        onClick = { isSecretVisible = !isSecretVisible },
                        modifier = Modifier.testTag("btn_toggle_visibility_$providerTag")
                    ) {
                        Icon(
                            imageVector = if (isSecretVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (isSecretVisible) {
                                stringResource(R.string.action_hide_key)
                            } else {
                                stringResource(R.string.action_show_key)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(portalUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        },
                        modifier = Modifier.testTag("btn_open_portal_$providerTag")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = stringResource(R.string.action_open_key_portal),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(inputTestTag)
        )

        KeyStatusBanner(
            state = verificationState,
            providerTag = providerTag
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onVerify(inputValue) },
                enabled = !isVerifying && (inputValue.isNotBlank() || effectiveKeyPresent),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_verify_key_$providerTag")
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.action_verify_key),
                    maxLines = 1
                )
            }

            Button(
                onClick = { onSaveOrReplace(inputValue) },
                enabled = !isVerifying && inputValue.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("btn_save_key_$providerTag")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Save,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.action_update_key),
                    maxLines = 1
                )
            }

            OutlinedButton(
                onClick = {
                    inputValue = ""
                    onDelete()
                },
                enabled = !isVerifying && (inputValue.isNotBlank() || storedKeyOverride.isNotBlank()),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                modifier = Modifier
                    .testTag("btn_delete_key_$providerTag")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete_key),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun KeyStatusBanner(
    state: KeyVerificationState,
    providerTag: String
) {
    when (state) {
        KeyVerificationState.Idle -> Unit

        KeyVerificationState.Verifying -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_verifying_$providerTag")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Checking models endpoint…",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        is KeyVerificationState.Valid -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_valid_$providerTag")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        is KeyVerificationState.Invalid -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_invalid_$providerTag")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
