package com.wusper.debarm

import android.app.Application
import com.wusper.debarm.core.EnvironmentController

class DebArmApp : Application() {
    lateinit var env: EnvironmentController
        private set

    override fun onCreate() {
        super.onCreate()
        env = EnvironmentController.get(this)
        env.refresh()
    }
}
