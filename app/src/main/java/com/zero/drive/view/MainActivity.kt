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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.zero.drive.R
import com.zero.drive.base.BaseActivity
import com.zero.drive.databinding.ActivityMainBinding
import com.zero.drive.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding

    companion object {
        const val ACCESS_TOKEN_KEY = "token"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        animateUI()
        lifecycleScope.launch { attachObservers() }

        binding.btnSignIn.setOnClickListener {
            mainViewModel.signInUsingCredentialManager(this)
        }
    }

    private fun attachObservers() {
        mainViewModel.authenticationSuccessful.observe(this) {//Auth using Google successful
            if (it) requestGoogleDriveAuthorization()
        }

        mainViewModel.authenticationFailed.observe(this) {//Auth failed and display error
            showToast(it)
        }
    }

    /**
     * 1. Checks if access to logged in user's Google Drive is available
     * 2. If available, retrieve access token
     * 3. Otherwise, launches an Intent to seek user approval
     */
    private fun requestGoogleDriveAuthorization() {
        val requestedScopes = listOf(Scope(DriveScopes.DRIVE_FILE)) //Define the scope of files access is needed
        val authorizationRequest = AuthorizationRequest.Builder()
            .setRequestedScopes(requestedScopes)
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(authorizationRequest)
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) { //Access to logged in user's Drive to be obtained
                    val pendingIntent = authorizationResult.pendingIntent
                    try {
                        val intentSenderRequest = pendingIntent?.let { IntentSenderRequest.Builder(it).build() }
                        intentSenderLauncher.launch(intentSenderRequest)
                    } catch (e: IntentSender.SendIntentException) {
                        Log.e("TAG", "Couldn't start Authorization UI: " + e.localizedMessage)
                    }
                } else { //Access to logged in user's Drive already obtained
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

    /**
     * 1. Launches a Intent
     * 2. Asks user for approval to access Drive
     * 3. If provided, retrieves access token to construct Drive object
     */
    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Handle the result
            val data = result.data

            val authorizationResult = Identity.getAuthorizationClient(this)
                .getAuthorizationResultFromIntent(data)
            val account: GoogleSignInAccount? = authorizationResult.toGoogleSignInAccount()

            if (account != null) { //Authorization is successful
                moveToHome(authorizationResult.accessToken)
            } else {
                Log.e("TAG", "onActivityResult: Authorization result account is null")
            }

        } else {
            Log.d("TAG", "Result not ok: ")
            // Handle other result codes or failure
        }
    }

    /**
     * Once successfully logged in and authorization for Drive obtained, move to Home
     * @param accessToken Access Token of currently logged in user. Needed to create Drive object
     */
    private fun moveToHome(accessToken: String?) {
        val intent = Intent(this, HomeActivity::class.java)
        intent.putExtra(ACCESS_TOKEN_KEY, accessToken)
        startActivity(intent)
        finish()
    }

    //Basic fade and slide animation for logo and sign in btn
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