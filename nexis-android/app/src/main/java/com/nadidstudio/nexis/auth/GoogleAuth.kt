package com.nadidstudio.nexis.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Real Google sign-in via Credential Manager. */
object GoogleAuth {
    /** "Web application" OAuth client ID from Google Cloud Console. Must be filled in. */
    const val WEB_CLIENT_ID = ""

    data class Account(val name: String, val email: String)

    /** [context] must be an Activity context. */
    suspend fun signIn(context: Context): Result<Account> {
        if (WEB_CLIENT_ID.isBlank()) return Result.failure(IllegalStateException("لم يتم ضبط Google Web Client ID بعد"))
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val cred = CredentialManager.create(context).getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val g = GoogleIdTokenCredential.createFrom(cred.data)
                Result.success(Account(g.displayName ?: "", g.id))
            } else Result.failure(IllegalStateException("نوع بيانات اعتماد غير مدعوم"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
