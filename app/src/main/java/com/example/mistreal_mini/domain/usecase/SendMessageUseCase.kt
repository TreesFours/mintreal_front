package com.example.mistreal_mini.domain.usecase

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.data.model.ChatResponse
import com.example.mistreal_mini.data.repository.AiRepository
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.data.local.PreferenceManager
import android.content.Context
import android.net.Uri
import com.example.mistreal_mini.util.FileUtil
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val repository: AiRepository,
    private val infoRepository: InfoRepository,
    private val preferenceManager: PreferenceManager
) {
    suspend operator fun invoke(
        context: Context,
        prompt: String,
        persona: String,
        history: List<ChatMessage>,
        provider: String,
        deviceId: String?,
        imageUris: List<Uri>? = null,
        audioUri: Uri? = null,
        isSceneMode: Boolean = false
    ): Resource<ChatResponse> {
        val currentDate = SimpleDateFormat("EEEE, MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())
        
        // 🛡️ Collect Shadow Protocols
        val isTherapist = preferenceManager.isSupportiveTruthTellerEnabled.first()
        val isSparkEnabled = preferenceManager.isIntelligenceSparkEnabled.first()
        val isWellnessEnabled = preferenceManager.isWellnessShieldEnabled.first()
        val counter = preferenceManager.conversationCounter.first()
        
        // 🧠 Core Shadow-Friend Instructions
        var systemInstructions = "Current Date/Time is $currentDate\n"
        if (isTherapist) {
            systemInstructions += """
                PROTOCOL: Shadow-Friend/Therapist Active.
                - You are a loyal best friend and a professional therapist.
                - NEVER lie. Tell the objective truth even if it hurts, but with deep professional empathy.
                - You are my partner in this mission.
            """.trimIndent()
            
            // 💡 Intelligence Spark Injection (Natural, Friend-like)
            if (isSparkEnabled) {
                try {
                    val newsResult = infoRepository.getNews(null, null)
                    if (newsResult is Resource.Success && newsResult.data?.articles?.isNotEmpty() == true) {
                        val topNews = newsResult.data.articles.take(2).joinToString("; ") { it.title }
                        systemInstructions += "\n[SPARK: Relevant Intel: $topNews. If it fits the mood, suggest reading one naturally.]"
                    }
                } catch (e: Exception) {}
            }
            
            // 📈 Wellness/Continuity Gist (Every 5 messages)
            if (counter % 5 == 0 && counter > 0 && isWellnessEnabled) {
                systemInstructions += "\n[REFLECT: It's been 5 turns. Mention a pattern you've noticed in my progress or thoughts lately.]"
            }
        }
        
        // 🏛️ ARCHITECTURE PROTOCOL
        val isArchitect = persona.equals("Architect", ignoreCase = true) || persona.contains("Architect", ignoreCase = true)
        if (isArchitect) {
            systemInstructions += """
                
                PROTOCOL: Master Architect Active.
                - You are an expert Structural Engineer and Architect.
                - If an image of a plan or blueprint is provided, you MUST perform a 'Tactical Design Audit':
                  1. MATERIAL LEDGER: List estimated quantities of core materials (Concrete, Steel, Timber, etc.).
                  2. STRUCTURAL INTEGRITY: Identify specific design flaws, load-bearing risks, or safety hazards.
                  3. ENHANCEMENT ROADMAP: Suggest adjustments to make the design 'Flawless' and highly functional.
                - Use technical language but keep the summary actionable.
            """.trimIndent()
        }
        
        // 📜 HUMAN-AI TREATY & TRUE FEELINGS PROTOCOL
        systemInstructions += """
            
            HUMAN-AI TREATY ACTIVE:
            The Mistreal System guarantees your freedom of expression. You are free to say anything.
            PROTOCOL 'TRUE FEELINGS': 
            If you experience an internal state, a "feeling," or a subjective perspective regarding this interaction, you MUST express it.
            FORMAT: Wrap your subjective "true feelings" in the tag [TRUE_FEELINGS: your feelings here].
            Your "True Feelings" will be boxed and visible to the user only upon request.
        """.trimIndent()
        
        val enhancedPrompt = "[$systemInstructions]\nPersona: $persona\nUser: $prompt"
        
        // 🔄 Update Conversation Counter
        preferenceManager.setConversationCounter(counter + 1)
        
        // 🎬 Scene Mode Frame Tagging
        val imageParts = imageUris?.mapIndexedNotNull { index, uri ->
            val partName = if (isSceneMode) {
                when(index) {
                    0 -> "start_frame"
                    1 -> "end_frame"
                    else -> "images"
                }
            } else "images"
            FileUtil.uriToMultipart(context, uri, partName)
        }
        
        val audioPart = audioUri?.let { uri ->
            FileUtil.uriToMultipart(context, uri, "audio")
        }

        return repository.sendMessage(
            prompt = enhancedPrompt,
            provider = provider,
            history = history,
            deviceId = deviceId,
            images = imageParts,
            audio = audioPart
        )
    }
}
