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
        delayMinutes: Int,
        guardianEnabled: Boolean? = null,
        contacts: List<EmergencyContact>? = null
    ): Resource<Boolean> {
        val result = infoRepository.updateUserSettings(
            deviceId = deviceId,
            userName = name,
            aiPersona = persona,
            autoReplyDelay = delayMinutes,
            guardianEnabled = guardianEnabled,
            emergencyContacts = contacts
        )

        if (result is Resource.Success) {
            preferenceManager.setUserName(name)
            preferenceManager.setAiPersona(persona)
            preferenceManager.setAutoReplyDelay(delayMinutes)
            guardianEnabled?.let { preferenceManager.setGuardianEnabled(it) }
        }
        return result
    }
}
