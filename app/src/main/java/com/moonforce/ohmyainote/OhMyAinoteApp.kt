package com.moonforce.ohmyainote

import android.app.Application
import com.moonforce.ohmyainote.di.AppContainer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class OhMyAinoteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        container = AppContainer(this)
    }
}
