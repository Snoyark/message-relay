package io.github.snoyark.messagerelay

import android.app.job.JobParameters
import android.app.job.JobService

// Retries sending once the phone has a network. Rescheduled with a backoff until the outbox is empty.
class SendJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Outbox.flush(this) { allSent -> jobFinished(params, !allSent && Prefs(this).configured) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
