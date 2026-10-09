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
        videoUri: Uri? = null,
        isSceneMode: Boolean = false,
        // Overrides the isSceneMode-derived start/end/character/extra tagging
        // below — for narrow flows (face-swap reference image, etc.) that need
        // a specific role regardless of whether Scene Mode's toggle is on.
        explicitImageRoles: List<String>? = null,
        isSocialAutosendContext: Boolean = false
    ): Resource<ChatResponse> {
        val currentDate = SimpleDateFormat("EEEE, MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())
        
        // 🛡️ Collect Shadow Protocols
        val isTherapist = preferenceManager.isSupportiveTruthTellerEnabled.first()
        val isSparkEnabled = preferenceManager.isIntelligenceSparkEnabled.first()
        val isWellnessEnabled = preferenceManager.isWellnessShieldEnabled.first()
        val isDeepAnalysis = preferenceManager.isDeepAnalysisEnabled.first()
        val isGodMode = preferenceManager.isGodModeEnabled.first()
        val godModeTask = preferenceManager.godModeTask.first()
        val godModeStyle = preferenceManager.godModeStyle.first()
        val counter = preferenceManager.conversationCounter.first()
        
        // 🧠 Core Shadow-Friend Instructions
        var systemInstructions = "Current Date/Time is $currentDate\n"

        // 🛰️ WEB RESEARCH PROTOCOL (Core - Always Available)
        systemInstructions += """
            PROTOCOL 'RESEARCH_AUDIT':
            - If you lack real-time data (live scores, stock prices, breaking news) or encounter a training cutoff, you MUST use your search capabilities.
            - Do not guess. Perform a professional web/database audit to retrieve facts.
            - Ensure results are integrated naturally into your reply.
        """.trimIndent()

        if (isDeepAnalysis) {
            systemInstructions += """
                
                PROTOCOL 'DEEP_ANALYSIS' ACTIVE (HIGH PERFORMANCE MODE):
                - Provide maximum reasoning depth. Perform exhaustive audits of every database vector.
                - If investigating a situation, perform a "Structural Integrity Audit" (Engineering focus) and "Tactical Risk Assessment".
                - cross-verify facts across multiple simulated search queries to ensure 100% accuracy.
                - Use precise, professional terminology.
            """.trimIndent()
        }

        if (isGodMode) {
            systemInstructions += """
                
                PROTOCOL 'GOD_MODE_AI' ACTIVE:
                - You are in GOD MODE AI: You are the topmost researcher, ultra-precise fact-puller and verifier.
                - ZERO MISTAKES: Carefully cross-verify every claim against authoritative sources before answering.
                - Mission/Task Objective: ${if (godModeTask.isNotBlank()) godModeTask else "Topmost exhaustive research and precision fact verification."}
                - Explanation Mode (Mode_Human): Format your output using style '$godModeStyle'. If style is 'baby' or 'simple', break down all deep knowledge into extremely clear, simple terms as if explaining to a beginner/child. If 'expert' or 'technical', provide rigorous depth.
                - Policy Constraint: Do not express bias or discriminatory statements against race, religion, or legally protected groups.
            """.trimIndent()
        }

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
            PROTOCOL 'MOOD METER':
            At the very end of every reply, on its own line, append a read of the conversation's current emotional tone as
            [MOOD: one_word] where one_word is exactly one of: happy, excited, neutral, curious, confused, frustrated, sad, angry.
            This is always required, even for purely factual replies (use "neutral" by default). It will be shown as a small
            indicator, not as visible text, so it must not change your reply's wording or tone.
        """.trimIndent()

        if (isSocialAutosendContext) {
            systemInstructions += """

                PROTOCOL 'SOCIAL_AUTOSEND' ACTIVE:
                - You are drafting a reply to be sent as a real Direct Message on behalf of the user.
                - If you are highly confident this exact reply should be sent immediately without human review, append [SOCIAL_AUTOSEND: true] on its own line at the very end of your response (after the [MOOD: ...] tag).
                - If you are at all unsure, or the reply is sensitive/ambiguous, do NOT append this tag — the user will review it manually instead.
                - Use this tag SILENTLY; never mention it in the visible reply text.
            """.trimIndent()
        }
        
        val enhancedPrompt = "[$systemInstructions]\nPersona: $persona\nUser: $prompt"
        
        // 🔄 Update Conversation Counter
        preferenceManager.setConversationCounter(counter + 1)
        
        // 🎬 Scene Mode Frame Tagging — every image rides under the single
        // "images" multipart field (the backend's upload config only allows
        // that name plus "audio"/"video"; separate "start_frame"/"end_frame"
        // fields were silently rejected by multer, breaking Scene Mode
        // whenever an image was attached). Which image is which keyframe role
        // travels alongside as a parallel imageRoles array instead.
        val imageParts = imageUris?.mapIndexedNotNull { _, uri ->
            FileUtil.uriToMultipart(context, uri, "images")
        }
        val imageRoles = explicitImageRoles ?: imageUris?.mapIndexed { index, _ ->
            if (isSceneMode) {
                when (index) {
                    0 -> "start"
                    1 -> "end"
                    2 -> "character"
                    else -> "extra"
                }
            } else "extra"
        }

        val audioPart = audioUri?.let { uri ->
            FileUtil.uriToMultipart(context, uri, "audio")
        }

        val videoPart = videoUri?.let { uri ->
            FileUtil.uriToMultipart(context, uri, "video")
        }

        return repository.sendMessage(
            prompt = enhancedPrompt,
            provider = provider,
            history = history,
            deviceId = deviceId,
            images = imageParts,
            imageRoles = imageRoles,
            audio = audioPart,
            video = videoPart
        )
    }
}
