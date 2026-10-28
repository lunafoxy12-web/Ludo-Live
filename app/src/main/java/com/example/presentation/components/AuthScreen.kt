package com.example.presentation.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Authentication screen providing Google Sign-In via Credential Manager,
 * Instant Player Guest login, and 50 free bonus coins celebration.
 * Engineered to never crash on lower-end devices or when Play Services is unconfigured.
 */
@Composable
fun AuthScreen(
    onAuthSuccess: (FirebaseUser) -> Unit,
    onPlayGuest: (displayName: String) -> Unit = {},
    onPlayOffline: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember {
        try {
            CredentialManager.create(context)
        } catch (t: Throwable) {
            Log.w("AuthScreen", "CredentialManager create warning: ${t.message}")
            null
        }
    }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Check if an existing Firebase user session already exists
    LaunchedEffect(Unit) {
        val currentUser = try {
            Firebase.auth.currentUser
        } catch (_: Throwable) {
            null
        }
        if (currentUser != null) {
            onAuthSuccess(currentUser)
        } else if (credentialManager != null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                scope = scope,
                onSuccess = { onAuthSuccess(it) },
                onFailure = { /* Show interactive buttons */ }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F0C29), Color(0xFF1B143F), Color(0xFF0D0B21))
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
            elevation = CardDefaults.cardElevation(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(28.dp))
                .border(1.5.dp, Color(0xFFFFB703).copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                .testTag("auth_screen_card")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Game Emblem
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E1065))
                        .border(2.dp, Color(0xFFFFD166), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "👑", fontSize = 38.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Ludo Live",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = "Real-Time Video & Social Multiplayer",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 50 FREE COINS REWARD BANNER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF047857).copy(alpha = 0.35f))
                        .border(1.dp, Color(0xFF10B981), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🎁 NEW PLAYER WELCOME BONUS",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Get 50 FREE Coins on Signup!",
                            color = Color(0xFFFFD166),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Create real player account & challenge real human opponents live",
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                errorMessage?.let { error ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF7F1D1D).copy(alpha = 0.45f))
                            .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFFECACA),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Primary "Sign in with Google" Button
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        try {
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                scope = scope,
                                onAuthSuccess = { user ->
                                    isLoading = false
                                    onAuthSuccess(user)
                                },
                                onAuthError = { err ->
                                    isLoading = false
                                    errorMessage = err
                                },
                                onAuthCancelled = {
                                    isLoading = false
                                }
                            )
                        } catch (t: Throwable) {
                            isLoading = false
                            Log.e("AuthScreen", "Direct sign-in catch: ${t.message}", t)
                            errorMessage = "Google Sign-In unavailable on this device. Tap 'Continue with 50 Free Coins' below!"
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("google_signin_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF1E1B4B),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "G", color = Color(0xFF4285F4), fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign in with Google",
                                color = Color(0xFF1E1B4B),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Instant Guest Player Account with 50 Free Coins (Never crashes, works on all devices!)
                Button(
                    onClick = {
                        onPlayGuest("Player_${(1000..9999).random()}")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("guest_signin_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "⚡", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Continue with 50 Free Coins (Instant)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Play Offline / Practice Mode Button
                Button(
                    onClick = {
                        onPlayOffline()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("play_offline_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "🎲", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Play Offline / Practice Mode",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Sign in for Google Cloud Sync, or tap Instant to play immediately with 50 free coins.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun findActivity(context: Context): Activity? {
    var ctx: Context? = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) {
            return ctx
        }
        ctx = ctx.baseContext
    }
    return null
}

private fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSuccess: (FirebaseUser) -> Unit,
    onFailure: () -> Unit
) {
    try {
        val targetActivity = findActivity(context) ?: run {
            onFailure()
            return
        }

        if (targetActivity.isFinishing || targetActivity.isDestroyed) {
            onFailure()
            return
        }

        val clientId = try {
            targetActivity.getString(R.string.default_web_client_id)
        } catch (_: Throwable) {
            onFailure()
            return
        }

        val googleIdOption = try {
            GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
        } catch (_: Throwable) {
            onFailure()
            return
        }

        val request = try {
            GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
        } catch (_: Throwable) {
            onFailure()
            return
        }

        scope.launch {
            try {
                val result = credentialManager.getCredential(targetActivity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                    authResult.user?.let(onSuccess) ?: onFailure()
                } else {
                    onFailure()
                }
            } catch (_: Throwable) {
                onFailure()
            }
        }
    } catch (_: Throwable) {
        onFailure()
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager?,
    scope: CoroutineScope,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit
) {
    try {
        if (credentialManager == null) {
            onAuthError("Google Sign-In is unavailable on this device. Tap 'Continue with 50 Free Coins' below!")
            return
        }

        val targetActivity = findActivity(context)
        if (targetActivity == null || targetActivity.isFinishing || targetActivity.isDestroyed) {
            onAuthError("Activity context unavailable. Tap 'Continue with 50 Free Coins' below!")
            return
        }

        val clientId = try {
            targetActivity.getString(R.string.default_web_client_id)
        } catch (_: Throwable) {
            onAuthError("Google Client ID configuration missing. Tap 'Continue with 50 Free Coins' below!")
            return
        }

        val signInOption = try {
            GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        } catch (t: Throwable) {
            Log.w("Auth", "GetSignInWithGoogleOption builder failed: ${t.message}")
            onAuthError("Google Sign-In not supported by device's Google Play Services. Tap 'Continue with 50 Free Coins' below!")
            return
        }

        val request = try {
            GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
        } catch (t: Throwable) {
            Log.w("Auth", "GetCredentialRequest builder failed: ${t.message}")
            onAuthError("Sign-in request error. Tap 'Continue with 50 Free Coins' below!")
            return
        }

        scope.launch {
            try {
                val result = credentialManager.getCredential(targetActivity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        onAuthSuccess(user)
                    } else {
                        onAuthError("Sign in completed, but user was null. Tap 'Continue with 50 Free Coins' below!")
                    }
                } else {
                    onAuthError("Unexpected credential type returned. Tap 'Continue with 50 Free Coins' below!")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.d("Auth", "Google Sign-In cancelled by user")
                onAuthCancelled()
            } catch (e: NoCredentialException) {
                Log.w("Auth", "NoCredentialException: ${e.message}")
                onAuthError("No Google account found on device. Tap 'Continue with 50 Free Coins' below to play!")
            } catch (e: GetCredentialProviderConfigurationException) {
                Log.w("Auth", "GetCredentialProviderConfigurationException: ${e.message}")
                onAuthError("Google Play Services provider not configured on this device. Tap 'Continue with 50 Free Coins' below!")
            } catch (e: GetCredentialCustomException) {
                val msg = e.message ?: ""
                Log.w("Auth", "GetCredentialCustomException: $msg")
                if (msg.contains("10") || msg.contains("12500") || msg.contains("DEVELOPER_ERROR")) {
                    onAuthError("Google Sign-In setup note: The APK's certificate SHA-1 must be added to Firebase Console for direct Google login. Tap 'Continue with 50 Free Coins' below to play immediately!")
                } else {
                    onAuthError("Sign-in note: $msg. Tap 'Continue with 50 Free Coins' below!")
                }
            } catch (e: GetCredentialException) {
                Log.w("Auth", "GetCredentialException: ${e.message}")
                onAuthError("Google Sign-In note: ${e.message ?: "Unavailable"}. Tap 'Continue with 50 Free Coins' below!")
            } catch (t: Throwable) {
                Log.e("Auth", "Unexpected sign-in failure", t)
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(t)
                } catch (_: Throwable) {}
                onAuthError("Sign-in note: ${t.localizedMessage ?: "Device error"}. Tap 'Continue with 50 Free Coins' below!")
            }
        }
    } catch (t: Throwable) {
        Log.e("Auth", "Synchronous onGoogleSignInClicked catch: ${t.message}", t)
        onAuthError("Sign-in could not be started. Tap 'Continue with 50 Free Coins' below!")
    }
}
