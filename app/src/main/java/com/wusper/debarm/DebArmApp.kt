package com.wusper.debarm

import android.app.Application
import com.wusper.debarm.core.EnvironmentController
import com.wusper.debarm.core.Pty

class DebArmApp : Application() {
    lateinit var env: EnvironmentController
        private set

    override fun onCreate() {
        super.onCreate()
        runCatching { Pty.ensureLoaded() }
        env = EnvironmentController.get(this)
        env.refresh()
    }
}
