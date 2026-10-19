package com.example.mistreal_mini.ui.chat

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.AiModelResponse
import com.example.mistreal_mini.data.repository.AiRepository
import com.example.mistreal_mini.data.repository.InfoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Fetches/categorizes available AI models for the provider drawer, and the
 * BYOK "has a custom provider configured" summary. Extracted out of
 * ChatViewModel, which still owns _selectedProvider itself (read via
 * getSelectedProvider/setSelectedProvider here) since that's genuinely
 * cross-cutting — read from many other places in the message-sending path.
 */
class AiProviderSelector(
    private val repository: AiRepository,
    private val infoRepository: InfoRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val isPro: () -> Boolean,
    private val getSelectedProvider: () -> String,
    private val setSelectedProvider: (String) -> Unit,
    private val emitEvent: suspend (String) -> Unit
) {
    private val _availableProviders = mutableStateListOf<AiModelResponse>()
    val availableProviders: List<AiModelResponse> = _availableProviders

    // 🛡️ Categorized Models for Tactical Drawer
    private val _categorizedModels = mutableStateOf<Map<String, List<AiModelResponse>>>(emptyMap())
    val categorizedModels: State<Map<String, List<AiModelResponse>>> = _categorizedModels

    // Drives the drawer's 3rd "Custom" tab — only shown once the user has
    // actually configured at least one BYOK text/video/image/video-gen provider.
    private val _hasCustomProvider = mutableStateOf(false)
    val hasCustomProvider: State<Boolean> = _hasCustomProvider

    private val _customProviderSummary = mutableStateOf<List<String>>(emptyList())
    val customProviderSummary: State<List<String>> = _customProviderSummary

    fun fetchAvailableModels() {
        scope.launch { fetchAvailableModelsAwait() }
    }

    // Exposed (not just the fire-and-forget fetchAvailableModels() above) so
    // ChatViewModel.onAuthenticated() can await it directly as part of its
    // sequential boot-loading steps.
    suspend fun fetchAvailableModelsAwait() {
        val deviceId = com.example.mistreal_mini.util.DeviceIdProvider(context).deviceId
        when (val result = repository.getAvailableModels(deviceId)) {
            is Resource.Success -> {
                val providers = result.data.orEmpty()
                _availableProviders.clear()

                _availableProviders.add(AiModelResponse("dynamic", "Dynamic (Best Fit)", "mistreal", false, "Free"))

                val filteredProviders = if (isPro()) {
                    providers
                } else {
                    providers.filter { it.price == "Free" || it.price.lowercase() == "free" }
                }
                _availableProviders.addAll(filteredProviders)

                if (filteredProviders.isEmpty() && !isPro()) {
                    emitEvent("Free tier has limited AI access. Upgrade to Premium for all models.")
                }

                if (filteredProviders.none { it.id == getSelectedProvider() } && getSelectedProvider() != "dynamic") {
                    setSelectedProvider("dynamic")
                }

                // 🛡️ Trigger Tactical Categorization
                groupModels(filteredProviders)
            }
            is Resource.Error -> {
                _availableProviders.clear()
                _availableProviders.add(AiModelResponse("dynamic", "Dynamic (Best Fit)", "mistreal", false, "Free"))
                setSelectedProvider("dynamic")
                emitEvent(result.message ?: "Could not reach the AI backend.")
            }
            else -> {}
        }
    }

    private fun groupModels(models: List<AiModelResponse>) {
        val groups = mutableMapOf<String, MutableList<AiModelResponse>>()

        // 1. COMMAND CENTER (Always at top)
        groups["COMMAND CENTER"] = mutableListOf(
            AiModelResponse("dynamic", "Mistreal Dynamic", "mistreal", false, "Free", "Optimized", 100)
        )

        models.forEach { model ->
            val id = model.id.lowercase()
            val category = when {
                // GLOBAL OVERLORD: Large models
                id.contains("gpt-4") || id.contains("claude-3") || id.contains("llama-3.1-405b") ||
                id.contains("grok") || id.contains("deepseek") || id.contains("llama-3.1-70b") ||
                (id.contains("gemini") && id.contains("pro") && !id.contains("flash")) -> "GLOBAL OVERLORD"

                // OPTIC INTEL: Vision/Video/Flash. Prefer real backend capability
                // metadata over id-substring guessing; fall back to the old
                // heuristic only when capabilities weren't provided (rollout safety).
                model.capabilities?.let { it.videoGen || it.voice || it.imageGen } == true -> "OPTIC INTEL"
                model.capabilities == null &&
                    (id.contains("vision") || id.contains("flash") || id.contains("video") || id.contains("kling")) -> "OPTIC INTEL"

                // GHOST PROTOCOL: Fast/Mini
                id.contains("mini") || id.contains("haiku") || id.contains("8b") || id.contains("instant") -> "GHOST PROTOCOL"

                // OPEN INTELLIGENCE: Everything else (Llama, Mistral, etc.)
                else -> "OPEN INTELLIGENCE"
            }
            groups.getOrPut(category) { mutableListOf() }.add(model)
        }

        _categorizedModels.value = groups
    }

    fun fetchHasCustomProvider() {
        scope.launch { fetchHasCustomProviderAwait() }
    }

    suspend fun fetchHasCustomProviderAwait() {
        val byokStatus = (infoRepository.getByokStatus() as? Resource.Success)?.data
        val byokVideoStatus = (infoRepository.getByokVideoStatus() as? Resource.Success)?.data
        val imageConfigs = (infoRepository.getMediaProviderConfigs("image_gen") as? Resource.Success)?.data?.configs.orEmpty()
        val videoConfigs = (infoRepository.getMediaProviderConfigs("video_gen") as? Resource.Success)?.data?.configs.orEmpty()

        val summary = buildList {
            if (byokStatus?.configured == true) add("💬 Text — ${byokStatus.providerType}${byokStatus.modelName?.let { " ($it)" } ?: ""}")
            if (byokVideoStatus?.configured == true) add("🎬 Video Edit — ${byokVideoStatus.providerType}${byokVideoStatus.modelName?.let { " ($it)" } ?: ""}")
            imageConfigs.forEach { add("🖼️ Image Gen — ${it.label}") }
            videoConfigs.forEach { add("📹 Video Gen — ${it.label}") }
        }
        _customProviderSummary.value = summary
        _hasCustomProvider.value = summary.isNotEmpty()
    }
}
