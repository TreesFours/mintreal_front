package com.example.mistreal_mini.domain.usecase

import com.example.mistreal_mini.data.Resource
import com.example.mistreal.data.models.SocialSyncResponse
import com.example.mistreal_mini.data.repository.InfoRepository
import javax.inject.Inject

class SyncSocialsUseCase @Inject constructor(
    private val infoRepository: InfoRepository
) {
    suspend operator fun invoke(deviceId: String?): Resource<SocialSyncResponse> {
        return infoRepository.syncSocials(deviceId)
    }
}
