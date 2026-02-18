package com.imhere.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.imhere.core.domain.port.SessionRepository
import org.koin.core.context.GlobalContext

class PurgeExpiredSessionsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val sessionRepository = GlobalContext.get().get<SessionRepository>()
            sessionRepository.purgeExpired(System.currentTimeMillis())
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
