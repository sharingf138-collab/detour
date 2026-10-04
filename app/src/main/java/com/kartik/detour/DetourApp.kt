package com.kartik.detour

import android.app.Application
import android.content.Context
import com.kartik.detour.data.ContentRepository
import com.kartik.detour.data.DayRepository
import com.kartik.detour.data.Prefs
import com.kartik.detour.data.StudyRepository
import com.kartik.detour.data.db.DetourDb
import com.kartik.detour.work.Refresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies; small enough that a DI framework would be overkill. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob())
    val db = DetourDb.create(context)
    val prefs = Prefs(context)
    val content = ContentRepository(context, db.cards(), scope)
    val study = StudyRepository(db.cards())
    val days = DayRepository(db.days())
    val notes = db.notes()
}

class DetourApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Refresh.createChannel(this)
        Refresh.scheduleDaily(this)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as DetourApp).container
