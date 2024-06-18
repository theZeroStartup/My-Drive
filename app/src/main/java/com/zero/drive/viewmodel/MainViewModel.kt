package com.zero.drive.viewmodel

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

class MainViewModel: ViewModel() {

    val authenticationSuccessful = MutableLiveData<Boolean>()
    val authenticationFailed = MutableLiveData<String>()

    fun signInUsingCredentialManager(context: Context) {
        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .setServerClientId("981366707748-niaboj66sqc5uka1k7n789fq6t9s5qoq.apps.googleusercontent.com") //Todo: Not a secure way, can use BuildConfig
            .setNonce(getNonce())
            .build()

        val credentialManager = CredentialManager.create(context)
        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = context,
                )
                handleSignIn(result)
            } catch (e: GetCredentialException) {
                Log.w("MainActivity", "signInResult:failed code=" + e.message)
            }
        }
    }

    /**
     * 1. Assess what type of credential is retrieved
     * 2. If Google credentials and token are retrieved as expected, success is returned
     * 3. Otherwise, a failure message is returned to UI
     */
    private fun handleSignIn(result: GetCredentialResponse) {
        when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        authenticationSuccessful.postValue(true)
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e("TAG", "Received an invalid google id token response", e)
                        authenticationFailed.postValue(e.message)
                    }
                } else {
                    // Catch any unrecognized custom credential type here.
                    authenticationFailed.postValue("Unexpected type of credential")
                    Log.e("TAG", "Unexpected type of credential")
                }
            }
            else -> {
                // Catch any unrecognized credential type here.
                authenticationFailed.postValue("Unexpected type of credential")
                Log.e("TAG", "Unexpected type of credential")
            }
        }
    }

    /**
     * Hashed Nonce to provide a secure way to authenticate
     * and to eliminate the chances of Replay attacks
    */
    private fun getNonce(): String {
        val uuid = UUID.randomUUID().toString().toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(uuid)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

}