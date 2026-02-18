package com.imhere.app

import android.app.Application
import com.imhere.app.di.appModule
import com.imhere.app.worker.PurgeExpiredSessionsWorker
import com.imhere.core.domain.port.KeywordRepository
import com.imhere.core.domain.usecase.SettingsSyncUseCase
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

class ImHereApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ImHereApplication)
            modules(appModule)
        }

        runBlocking {
            val keywordRepository = getKoin().get<KeywordRepository>()
            if (keywordRepository.get().isEmpty()) {
                keywordRepository.saveKeywords(listOf("폰아 울려"))
            }
        }
        getKoin().get<SettingsSyncUseCase>().start()
        getKoin().get<AppBootstrap>().boot()
        scheduleRetentionWorker()
    }

    private fun scheduleRetentionWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<PurgeExpiredSessionsWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "imhere_session_retention",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
