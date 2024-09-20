package com.example.plantpal

import android.app.Application
import com.google.firebase.FirebaseApp

class PlantPalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}