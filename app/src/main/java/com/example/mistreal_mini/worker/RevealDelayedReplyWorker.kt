package com.example.mistreal_mini.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mistreal_mini.data.repository.AiRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One-shot delayed reveal for a minichat reply held back by the per-thread
 * delay toggle (see ChatViewModel.performChatRequest). Clearing revealAt is
 * itself the reveal — the DAO's read queries filter on "revealAt IS NULL",
 * so this single UPDATE both un-hides the row and, being a write, makes
 * Room's Flow re-emit it. Scheduled with setInitialDelay so it survives the
 * app process being killed, unlike a ViewModel-scoped delay() would.
 */
@HiltWorker
class RevealDelayedReplyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val aiRepository: AiRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MESSAGE_ID = "messageId"
    }

    override suspend fun doWork(): Result {
        val messageId = inputData.getLong(KEY_MESSAGE_ID, -1L)
        if (messageId < 0) return Result.failure()
        aiRepository.clearRevealAt(messageId)
        return Result.success()
    }
}
