package com.example.pguard

import android.app.Application
import com.example.pguard.engine.accessibility.PGuardAccessibilityService

class PGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PGuardAccessibilityService.loadSettings(this)
        com.example.pguard.presentation.focus.FocusModeManager.init(this)
    }
}
