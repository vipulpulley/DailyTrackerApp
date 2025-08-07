package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInButton: SignInButton
    private lateinit var anonymousSignInButton: Button

    private val RC_SIGN_IN = 9001 // Request code for Google Sign-In

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        Log.d("LOGIN_ACTIVITY", "LoginActivity onCreate started.")

        // Initialize Firebase
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        auth = Firebase.auth

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Get UI references
        googleSignInButton = findViewById(R.id.googleSignInButton)
        anonymousSignInButton = findViewById(R.id.anonymousSignInButton)

        // Set up listeners
        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }

        anonymousSignInButton.setOnClickListener {
            signInAnonymously()
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is already signed in (Firebase session persists)
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d("LOGIN_ACTIVITY", "User already signed in: ${currentUser.uid}. Navigating to ProfileSelectionActivity.")
            navigateToProfileSelectionActivity(currentUser)
        } else {
            Log.d("LOGIN_ACTIVITY", "No user signed in. Displaying login options.")
        }
    }

    /**
     * Initiates the Google Sign-In flow.
     */
    private fun signInWithGoogle() {
        Log.d("LOGIN_ACTIVITY", "Starting Google Sign-In intent.")
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    /**
     * Handles the result from the Google Sign-In intent.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)!!
                Log.d("LOGIN_ACTIVITY", "Google sign in successful. Authenticating with Firebase.")
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: com.google.android.gms.common.api.ApiException) {
                Log.w("LOGIN_ACTIVITY", "Google sign in failed", e)
                Toast.makeText(this, "Google Sign-In failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Authenticates with Firebase using a Google ID Token.
     * Handles linking anonymous accounts if applicable.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = auth.currentUser // Get current user (could be anonymous)

        if (currentUser != null && currentUser.isAnonymous) {
            // User is currently anonymous, link the Google account
            Log.d("LOGIN_ACTIVITY", "Linking anonymous account (${currentUser.uid}) with Google credential.")
            currentUser.linkWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d("LOGIN_ACTIVITY", "Account linking successful.")
                        Toast.makeText(this, "Account linked with Google!", Toast.LENGTH_SHORT).show()
                        navigateToProfileSelectionActivity(auth.currentUser)
                    } else {
                        Log.w("LOGIN_ACTIVITY", "Account linking failed", task.exception)
                        Toast.makeText(this, "Account linking failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            // No anonymous user, or already signed in with a different provider, sign in directly
            Log.d("LOGIN_ACTIVITY", "Signing in with Google credential directly.")
            auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d("LOGIN_ACTIVITY", "Firebase sign in with Google successful.")
                        Toast.makeText(this, "Signed in with Google!", Toast.LENGTH_SHORT).show()
                        navigateToProfileSelectionActivity(auth.currentUser)
                    } else {
                        Log.w("LOGIN_ACTIVITY", "Firebase sign in with Google failed", task.exception)
                        Toast.makeText(this, "Firebase Sign-In failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    /**
     * Signs in anonymously with Firebase.
     */
    private fun signInAnonymously() {
        Log.d("LOGIN_ACTIVITY", "Attempting anonymous sign-in.")
        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LOGIN_ACTIVITY", "Anonymous sign-in successful. UID: ${auth.currentUser?.uid}")
                    Toast.makeText(this, "Signed in as Guest!", Toast.LENGTH_SHORT).show()
                    navigateToProfileSelectionActivity(auth.currentUser)
                } else {
                    Log.w("LOGIN_ACTIVITY", "Anonymous sign-in failed", task.exception)
                    Toast.makeText(this, "Guest sign-in failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    /**
     * Navigates to ProfileSelectionActivity and finishes LoginActivity.
     * @param user The FirebaseUser who just authenticated.
     */
    private fun navigateToProfileSelectionActivity(user: FirebaseUser?) {
        if (user != null) {
            val intent = Intent(this, ProfileSelectionActivity::class.java)
            startActivity(intent)
            finish() // Finish LoginActivity so user can't go back to it with back button
        } else {
            Log.e("LOGIN_ACTIVITY", "Attempted to navigate to ProfileSelectionActivity with null user.")
            Toast.makeText(this, "Authentication failed. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }
}
