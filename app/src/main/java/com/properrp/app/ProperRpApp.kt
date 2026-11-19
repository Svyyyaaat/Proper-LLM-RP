package com.properrp.app

import android.app.Application

class ProperRpApp : Application() {
    val state by lazy { AppState(this) }
}
