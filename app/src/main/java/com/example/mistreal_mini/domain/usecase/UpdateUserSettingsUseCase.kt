package com.example.mistreal_mini.domain.usecase

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.EmergencyContact
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.data.repository.InfoRepository
import javax.inject.Inject

class UpdateUserSettingsUseCase @Inject constructor(
    private val infoRepository: InfoRepository,
    private val preferenceManager: PreferenceManager
) {
    suspend operator fun invoke(
        deviceId: String,
        name: String,
        persona: String,
        audience: String,
        delayMinutes: Int,
        guardianEnabled: Boolean? = null,
        contacts: List<EmergencyContact>? = null,
        aiAutoSendEnabled: Boolean? = null
    ): Resource<Boolean> {
        val result = infoRepository.updateUserSettings(
            deviceId = deviceId,
            userName = name,
            aiPersona = persona,
            aiAudience = audience,
            autoReplyDelay = delayMinutes,
            guardianEnabled = guardianEnabled,
            emergencyContacts = contacts,
            aiAutoSendEnabled = aiAutoSendEnabled
        )

        if (result is Resource.Success) {
            preferenceManager.setUserName(name)
            preferenceManager.setAiPersona(persona)
            preferenceManager.setAiAudience(audience)
            preferenceManager.setAutoReplyDelay(delayMinutes)
            guardianEnabled?.let { preferenceManager.setGuardianEnabled(it) }
            aiAutoSendEnabled?.let { preferenceManager.setAiAutoSendEnabled(it) }
        }
        return result
    }
}
