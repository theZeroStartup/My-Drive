package com.zero.drive.view

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.transition.TransitionManager
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.AnimationUtils
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.zero.drive.R
import com.zero.drive.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var credentialManager: CredentialManager

    companion object {
        const val ACCESS_TOKEN_KEY = "token"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        animateUI()

        credentialManager = CredentialManager.create(this)

        binding.btnSignIn.setOnClickListener {
            signInUsingCredManager()
        }
    }

    private fun signInUsingCredManager() {
        val uuid = UUID.randomUUID().toString().toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(uuid)
        val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .setServerClientId("981366707748-niaboj66sqc5uka1k7n789fq6t9s5qoq.apps.googleusercontent.com") //Todo: Not a secure way, can use BuildConfig
            .setNonce(hashedNonce)
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@MainActivity,
                )
                handleSignIn(result)
            } catch (e: GetCredentialException) {
                Log.w("MainActivity", "signInResult:failed code=" + e.message)
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        requestGoogleDriveAuthorization()
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e("TAG", "Received an invalid google id token response", e)
                    }
                } else {
                    // Catch any unrecognized custom credential type here.
                    Log.e("TAG", "Unexpected type of credential")
                }
            }

            else -> {
                // Catch any unrecognized credential type here.
                Log.e("TAG", "Unexpected type of credential")
            }
        }
    }

    private fun requestGoogleDriveAuthorization() {
        val requestedScopes = listOf(Scope(DriveScopes.DRIVE_FILE))
        val authorizationRequest = AuthorizationRequest.Builder()
            .setRequestedScopes(requestedScopes)
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(authorizationRequest)
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    val pendingIntent = authorizationResult.pendingIntent
                    try {
                        val intentSenderRequest = pendingIntent?.let { IntentSenderRequest.Builder(it).build() }
                        intentSenderLauncher.launch(intentSenderRequest)
                    } catch (e: IntentSender.SendIntentException) {
                        Log.e("TAG", "Couldn't start Authorization UI: " + e.localizedMessage)
                    }
                } else {
                    val account = authorizationResult.toGoogleSignInAccount()

                    if (account != null) {
                        moveToHome(authorizationResult.accessToken)
                    } else {
                        Log.e("TAG", "Authorization result account is null")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("TAG", "Failed to authorize", e)
            }
    }

    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Handle the result
            val data = result.data

            val authorizationResult = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)
            val account: GoogleSignInAccount? = authorizationResult.toGoogleSignInAccount()
            if (account != null) {
                moveToHome(authorizationResult.accessToken)
            } else {
                Log.e("TAG", "onActivityResult: Authorization result account is null")
            }
        } else {
            Log.d("TAG", "Result not ok: ")
            // Handle other result codes or failure
        }
    }

    private fun moveToHome(accessToken: String?) {
        val intent = Intent(this, HomeActivity::class.java)
        intent.putExtra(ACCESS_TOKEN_KEY, accessToken)
        startActivity(intent)
        finish()
    }

    private fun animateUI() {
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        binding.llLogoContainer.startAnimation(slideUp)
        slideUp.setAnimationListener(object : AnimationListener {
            override fun onAnimationStart(p0: Animation?) = Unit
            override fun onAnimationRepeat(p0: Animation?)  = Unit
            override fun onAnimationEnd(p0: Animation?) {
                TransitionManager.beginDelayedTransition(binding.root)
                binding.btnSignIn.visibility = View.VISIBLE
            }
        })
    }
}