package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppSettings
import com.example.data.model.SummaryProvider
import com.example.data.model.TargetLanguage
import com.example.data.model.TitleTranslationProvider

@Composable
fun TranslateSettingsPage(
    settings: AppSettings,
    isRefreshingGeminiModels: Boolean,
    isRefreshingOllamaModels: Boolean,
    onSelectLanguage: (TargetLanguage) -> Unit,
    onSelectTranslator: (TitleTranslationProvider) -> Unit,
    onSelectSummaryProvider: (SummaryProvider) -> Unit,
    onSelectGeminiModel: (String) -> Unit,
    onSelectOllamaModel: (String) -> Unit,
    onRefreshGeminiModels: () -> Unit,
    onRefreshOllamaModels: () -> Unit,
    onToggleCustomTranslatePrompt: (Boolean) -> Unit = {},
    onUpdateCustomTranslatePrompt: (String) -> Unit = {},
    onToggleCustomSummarizePrompt: (Boolean) -> Unit = {},
    onUpdateCustomSummarizePrompt: (String) -> Unit = {},
    onBack: () -> Unit
) {
    var langExpanded by remember { mutableStateOf(false) }
    var translatorExpanded by remember { mutableStateOf(false) }
    var geminiModelsExpanded by remember { mutableStateOf(false) }
    var ollamaModelsExpanded by remember { mutableStateOf(false) }
    val noApiKeyBadge = stringResource(R.string.translator_key_required)

    fun isTranslatorEnabled(provider: TitleTranslationProvider): Boolean {
        return when (provider) {
            TitleTranslationProvider.GEMINI -> settings.effectiveGeminiKey.isNotBlank()
            TitleTranslationProvider.OLLAMA -> settings.effectiveOllamaKey.isNotBlank()
            TitleTranslationProvider.DEEPL_API -> settings.effectiveDeeplKey.isNotBlank()
            else -> true
        }
    }

    val isAiTranslatorSelected = settings.titleProvider == TitleTranslationProvider.GEMINI ||
        settings.titleProvider == TitleTranslationProvider.OLLAMA

    SettingsSubPageScaffold(
        title = stringResource(R.string.settings_translate_languages),
        onBack = onBack
    ) {
        // 1. Language Choice
        SettingsSectionCard(
            title = stringResource(R.string.settings_target_lang),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            CollapsibleDropdownSelector(
                selectedLabel = settings.targetLanguage.displayLabel,
                selectedBadge = settings.targetLanguage.isoCode.uppercase(),
                expanded = langExpanded,
                onToggleExpanded = { langExpanded = !langExpanded },
                items = TargetLanguage.entries,
                isItemSelected = { it == settings.targetLanguage },
                itemLabel = { it.displayLabel },
                itemBadge = { it.isoCode.uppercase() },
                onSelectItem = { lang ->
                    onSelectLanguage(lang)
                    langExpanded = false
                },
                toggleTestTag = "dropdown_language_toggle",
                itemTestTagPrefix = "dropdown_lang_"
            )
        }

        // 2. Translator (+ AI Translate Prompt when Gemini or Ollama is selected)
        SettingsSectionCard(
            title = stringResource(R.string.settings_translator_choice),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            CollapsibleDropdownSelector(
                selectedLabel = settings.titleProvider.label,
                selectedBadge = null,
                expanded = translatorExpanded,
                onToggleExpanded = { translatorExpanded = !translatorExpanded },
                items = TitleTranslationProvider.entries,
                isItemSelected = { it == settings.titleProvider },
                isItemEnabled = ::isTranslatorEnabled,
                itemLabel = { it.label },
                itemBadge = { provider ->
                    if (!isTranslatorEnabled(provider)) noApiKeyBadge else null
                },
                onSelectItem = { provider ->
                    if (isTranslatorEnabled(provider)) {
                        onSelectTranslator(provider)
                        translatorExpanded = false
                    }
                },
                toggleTestTag = "dropdown_translator_toggle",
                itemTestTag = { _, provider -> "translator_option_${provider.name}" }
            )

            if (isAiTranslatorSelected) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                AiPromptCustomizationSection(
                    isCustomEnabled = settings.useCustomTranslatePrompt,
                    customPrompt = settings.customTranslatePrompt,
                    defaultPrompt = AppSettings.DEFAULT_TRANSLATE_PROMPT,
                    onToggleCustom = onToggleCustomTranslatePrompt,
                    onUpdatePrompt = onUpdateCustomTranslatePrompt,
                    switchTag = "switch_custom_translate_prompt",
                    inputTag = "input_translate_prompt"
                )
            }
        }

        // 3. Summarize (Gemini & Ollama + Models + Summarize Prompt)
        SettingsSectionCard(
            title = stringResource(R.string.settings_summary_provider),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryProvider.entries.forEach { provider ->
                    val selected = settings.summaryProvider == provider
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectSummaryProvider(provider) },
                        label = {
                            Text(
                                text = provider.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chip_summary_provider_${provider.name}")
                    )
                }
            }

            if (settings.summaryProvider == SummaryProvider.GEMINI) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.models_available_count, settings.availableModels.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("text_models_count")
                        )
                        OutlinedButton(
                            onClick = onRefreshGeminiModels,
                            enabled = !isRefreshingGeminiModels,
                            modifier = Modifier.testTag("btn_refresh_models")
                        ) {
                            if (isRefreshingGeminiModels) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_refresh_models))
                        }
                    }

                    CollapsibleDropdownSelector(
                        selectedLabel = settings.selectedModel,
                        selectedBadge = null,
                        expanded = geminiModelsExpanded,
                        onToggleExpanded = { geminiModelsExpanded = !geminiModelsExpanded },
                        items = settings.availableModels,
                        isItemSelected = { it == settings.selectedModel },
                        itemLabel = { it },
                        itemBadge = { null },
                        onSelectItem = { modelName ->
                            onSelectGeminiModel(modelName)
                            geminiModelsExpanded = false
                        },
                        toggleTestTag = "dropdown_models_toggle",
                        itemTestTagPrefix = "model_item_"
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.models_available_count, settings.availableOllamaModels.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("text_models_count")
                        )
                        OutlinedButton(
                            onClick = onRefreshOllamaModels,
                            enabled = !isRefreshingOllamaModels,
                            modifier = Modifier.testTag("btn_refresh_ollama_models")
                        ) {
                            if (isRefreshingOllamaModels) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_refresh_models))
                        }
                    }

                    CollapsibleDropdownSelector(
                        selectedLabel = settings.selectedOllamaModel,
                        selectedBadge = null,
                        expanded = ollamaModelsExpanded,
                        onToggleExpanded = { ollamaModelsExpanded = !ollamaModelsExpanded },
                        items = settings.availableOllamaModels,
                        isItemSelected = { it == settings.selectedOllamaModel },
                        itemLabel = { it },
                        itemBadge = { null },
                        onSelectItem = { modelName ->
                            onSelectOllamaModel(modelName)
                            ollamaModelsExpanded = false
                        },
                        toggleTestTag = "dropdown_ollama_models_toggle",
                        itemTestTagPrefix = "ollama_model_item_"
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            AiPromptCustomizationSection(
                isCustomEnabled = settings.useCustomSummarizePrompt,
                customPrompt = settings.customSummarizePrompt,
                defaultPrompt = AppSettings.DEFAULT_SUMMARIZE_PROMPT,
                onToggleCustom = onToggleCustomSummarizePrompt,
                onUpdatePrompt = onUpdateCustomSummarizePrompt,
                switchTag = "switch_custom_summarize_prompt",
                inputTag = "input_summarize_prompt"
            )
        }
    }
}

@Composable
private fun AiPromptCustomizationSection(
    isCustomEnabled: Boolean,
    customPrompt: String,
    defaultPrompt: String,
    onToggleCustom: (Boolean) -> Unit,
    onUpdatePrompt: (String) -> Unit,
    switchTag: String,
    inputTag: String
) {
    var draftPrompt by remember {
        mutableStateOf(if (isCustomEnabled) customPrompt else defaultPrompt)
    }
    var directionMode by rememberSaveable { mutableStateOf("auto") }

    LaunchedEffect(isCustomEnabled, customPrompt) {
        if (!isCustomEnabled) {
            draftPrompt = defaultPrompt
        } else if (draftPrompt != customPrompt) {
            draftPrompt = customPrompt
        }
    }

    val isDraftArabic = remember(draftPrompt) {
        draftPrompt.any {
            it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' ||
            it in '\u08A0'..'\u08FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF'
        }
    }

    val isRtl = when (directionMode) {
        "rtl" -> true
        "ltr" -> false
        else -> isDraftArabic
    }

    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr
    val textAlign = if (isRtl) TextAlign.Right else TextAlign.Left

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_custom_prompt_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isCustomEnabled) {
                    Text(
                        text = stringResource(R.string.settings_default_prompt_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = isCustomEnabled,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        draftPrompt = defaultPrompt
                    } else {
                        draftPrompt = customPrompt.ifBlank { defaultPrompt }
                    }
                    onToggleCustom(enabled)
                },
                modifier = Modifier.testTag(switchTag)
            )
        }

        if (isCustomEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.prompt_direction_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                listOf(
                    "auto" to stringResource(R.string.prompt_direction_auto),
                    "rtl" to stringResource(R.string.prompt_direction_rtl),
                    "ltr" to stringResource(R.string.prompt_direction_ltr)
                ).forEach { (mode, label) ->
                    val selected = directionMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { directionMode = mode },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("chip_prompt_dir_$mode")
                    )
                }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            OutlinedTextField(
                value = if (isCustomEnabled) draftPrompt else defaultPrompt,
                onValueChange = { updated ->
                    if (isCustomEnabled) {
                        draftPrompt = updated
                        onUpdatePrompt(updated)
                    }
                },
                textStyle = LocalTextStyle.current.copy(
                    textDirection = textDirection,
                    textAlign = textAlign
                ),
                enabled = isCustomEnabled,
                readOnly = !isCustomEnabled,
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(inputTag)
            )
        }
    }
}

