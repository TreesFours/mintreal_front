package com.example.mistreal_mini.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ONBOARDED_KEY = booleanPreferencesKey("is_onboarded")
    private val SOCIAL_SYNC_ENABLED_KEY = booleanPreferencesKey("social_sync_enabled")
    private val AUTO_APPROVE_ACTIONS_KEY = booleanPreferencesKey("auto_approve_actions")
    private val USER_NAME_KEY = androidx.datastore.preferences.core.stringPreferencesKey("user_name")
    private val AI_PERSONA_KEY = androidx.datastore.preferences.core.stringPreferencesKey("ai_persona")
    private val AI_CUSTOM_NAME_KEY = androidx.datastore.preferences.core.stringPreferencesKey("ai_custom_name")
    private val AI_AUDIENCE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("ai_audience")
    private val AUTO_REPLY_DELAY_KEY = androidx.datastore.preferences.core.intPreferencesKey("auto_reply_delay")
    private val IS_PRO_KEY = booleanPreferencesKey("is_pro")
    private val GUARDIAN_ENABLED_KEY = booleanPreferencesKey("guardian_enabled")
    private val EMERGENCY_CONTACTS_KEY = androidx.datastore.preferences.core.stringPreferencesKey("emergency_contacts")
    private val LOCATION_ENABLED_KEY = booleanPreferencesKey("location_enabled")
    private val TTS_ENABLED_KEY = booleanPreferencesKey("tts_enabled")
    private val STT_ENABLED_KEY = booleanPreferencesKey("stt_enabled")
    private val TTS_VOICE_NAME_KEY = androidx.datastore.preferences.core.stringPreferencesKey("tts_voice_name")
    private val CUSTOM_PERSONAS_KEY = androidx.datastore.preferences.core.stringPreferencesKey("custom_personas")
    private val CUSTOM_AUDIENCES_KEY = androidx.datastore.preferences.core.stringPreferencesKey("custom_audiences")
    private val SUPPORTIVE_TRUTH_TELLER_KEY = booleanPreferencesKey("supportive_truth_teller_enabled")
    private val WELLNESS_SHIELD_KEY = booleanPreferencesKey("wellness_shield_enabled")
    private val PROACTIVE_NUDGE_KEY = booleanPreferencesKey("proactive_nudge_enabled")
    private val INTELLIGENCE_SPARK_KEY = booleanPreferencesKey("intelligence_spark_enabled")
    private val PERSISTENT_SCENE_MODE_KEY = booleanPreferencesKey("persistent_scene_mode_enabled")
    private val DEFAULT_TRANSLATION_LANG_KEY = androidx.datastore.preferences.core.stringPreferencesKey("default_translation_lang")
    private val CONVERSATION_COUNTER_KEY = androidx.datastore.preferences.core.intPreferencesKey("conversation_counter")
    private val THEME_MODE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("theme_mode") // "fire", "sand", "auto"
    private val LAST_INTERACTION_TIME_KEY = androidx.datastore.preferences.core.longPreferencesKey("last_interaction_time")

    val isOnboarded: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ONBOARDED_KEY] ?: false
        }

    val isPro: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_PRO_KEY] ?: false
        }

    val userName: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[USER_NAME_KEY] ?: "User"
        }

    val aiPersona: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[AI_PERSONA_KEY] ?: "Shadow"
        }

    val aiCustomName: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[AI_CUSTOM_NAME_KEY] ?: "Shadow AI"
        }

    val aiAudience: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[AI_AUDIENCE_KEY] ?: "None"
        }

    val autoReplyDelay: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_REPLY_DELAY_KEY] ?: 15
        }

    val isSocialSyncEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SOCIAL_SYNC_ENABLED_KEY] ?: false
        }

    val isAutoApproveActions: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_APPROVE_ACTIONS_KEY] ?: false
        }

    val guardianEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[GUARDIAN_ENABLED_KEY] ?: false
        }

    val emergencyContacts: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[EMERGENCY_CONTACTS_KEY] ?: "[]"
        }

    val isLocationEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[LOCATION_ENABLED_KEY] ?: false
        }

    val isTtsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[TTS_ENABLED_KEY] ?: true
        }

    val isSttEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[STT_ENABLED_KEY] ?: true
        }

    val ttsVoiceName: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[TTS_VOICE_NAME_KEY]
        }

    val customPersonas: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[CUSTOM_PERSONAS_KEY] ?: "[]"
        }

    val customAudiences: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[CUSTOM_AUDIENCES_KEY] ?: "[]"
        }

    val isSupportiveTruthTellerEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SUPPORTIVE_TRUTH_TELLER_KEY] ?: false
        }

    val isWellnessShieldEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[WELLNESS_SHIELD_KEY] ?: false
        }

    val isProactiveNudgeEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PROACTIVE_NUDGE_KEY] ?: false
        }

    val isIntelligenceSparkEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[INTELLIGENCE_SPARK_KEY] ?: false
        }

    val isPersistentSceneModeEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PERSISTENT_SCENE_MODE_KEY] ?: false
        }

    val defaultTranslationLang: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DEFAULT_TRANSLATION_LANG_KEY] ?: "English"
        }

    val conversationCounter: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[CONVERSATION_COUNTER_KEY] ?: 0
        }

    val themeMode: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_MODE_KEY] ?: "auto"
        }

    val lastInteractionTime: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_INTERACTION_TIME_KEY] ?: System.currentTimeMillis()
        }

    suspend fun setOnboarded(onboarded: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDED_KEY] = onboarded
        }
    }

    suspend fun setSocialSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SOCIAL_SYNC_ENABLED_KEY] = enabled
        }
    }

    suspend fun setAutoApproveActions(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_APPROVE_ACTIONS_KEY] = enabled
        }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = name
        }
    }

    suspend fun setAiPersona(persona: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_PERSONA_KEY] = persona
        }
    }

    suspend fun setAiCustomName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_CUSTOM_NAME_KEY] = name
        }
    }

    suspend fun setAiAudience(audience: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_AUDIENCE_KEY] = audience
        }
    }

    suspend fun setAutoReplyDelay(delayMinutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_REPLY_DELAY_KEY] = delayMinutes
        }
    }

    suspend fun setPro(isPro: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_PRO_KEY] = isPro
        }
    }

    suspend fun setGuardianEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[GUARDIAN_ENABLED_KEY] = enabled
        }
    }

    suspend fun setEmergencyContacts(contactsJson: String) {
        context.dataStore.edit { preferences ->
            preferences[EMERGENCY_CONTACTS_KEY] = contactsJson
        }
    }

    suspend fun setLocationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LOCATION_ENABLED_KEY] = enabled
        }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TTS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setSttEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[STT_ENABLED_KEY] = enabled
        }
    }

    suspend fun setTtsVoiceName(voiceName: String) {
        context.dataStore.edit { preferences ->
            preferences[TTS_VOICE_NAME_KEY] = voiceName
        }
    }

    suspend fun setCustomPersonas(json: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_PERSONAS_KEY] = json
        }
    }

    suspend fun setCustomAudiences(json: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_AUDIENCES_KEY] = json
        }
    }

    suspend fun setSupportiveTruthTellerEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SUPPORTIVE_TRUTH_TELLER_KEY] = enabled
        }
    }

    suspend fun setWellnessShieldEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WELLNESS_SHIELD_KEY] = enabled
        }
    }

    suspend fun setProactiveNudgeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PROACTIVE_NUDGE_KEY] = enabled
        }
    }

    suspend fun setIntelligenceSparkEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[INTELLIGENCE_SPARK_KEY] = enabled
        }
    }

    suspend fun setPersistentSceneModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PERSISTENT_SCENE_MODE_KEY] = enabled
        }
    }

    suspend fun setDefaultTranslationLang(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_TRANSLATION_LANG_KEY] = lang
        }
    }

    suspend fun setConversationCounter(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[CONVERSATION_COUNTER_KEY] = count
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    suspend fun setLastInteractionTime(time: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_INTERACTION_TIME_KEY] = time
        }
    }
}
