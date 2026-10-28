package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.core.billing.BillingService
import com.example.core.sound.SoundManager
import com.example.data.repository.FirestoreRepository
import com.example.presentation.LudoLiveMainScreen
import com.example.presentation.viewmodel.LudoGameViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {

    private val viewModel: LudoGameViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                try {
                    if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                        FirebaseApp.initializeApp(applicationContext)
                    }
                } catch (t: Throwable) {
                    Log.w("MainActivity", "FirebaseApp init warning: ${t.message}")
                }

                val soundMgr = SoundManager(applicationContext)
                val billingSvc = BillingService()
                val firestoreRepo = try {
                    FirestoreRepository.create(applicationContext)
                } catch (t: Throwable) {
                    Log.w("MainActivity", "FirestoreRepo init warning: ${t.message}")
                    null
                }

                return LudoGameViewModel(
                    firestoreRepository = firestoreRepo,
                    soundManager = soundMgr,
                    billingService = billingSvc
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (t: Throwable) {
            Log.w("MainActivity", "enableEdgeToEdge warning: ${t.message}")
        }

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            val currentUser = try {
                Firebase.auth.currentUser
            } catch (t: Throwable) {
                Log.w("MainActivity", "Firebase.auth check warning: ${t.message}")
                null
            }
            if (currentUser != null) {
                viewModel.onUserAuthenticated(currentUser)
            }
        } catch (t: Throwable) {
            Log.e("MainActivity", "Firebase startup check error: ${t.message}", t)
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B091A)
                ) {
                    LudoLiveMainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            viewModel.soundManager.release()
        } catch (_: Exception) {}
    }
}
