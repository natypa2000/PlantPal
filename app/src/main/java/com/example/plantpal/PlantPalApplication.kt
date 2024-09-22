package com.example.plantpal

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PlantPalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        setupTokenRefresh()
    }

    private fun setupTokenRefresh() {
        FirebaseAuth.getInstance().addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val result = user.getIdToken(true).await()
                        Log.d("PlantPalApplication", "Token refreshed successfully")
                    } catch (e: Exception) {
                        Log.e("PlantPalApplication", "Error refreshing token: ${e.message}")
                    }
                }
            }
        }
    }
}