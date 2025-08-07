package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // UI elements
    private lateinit var userNameEditText: EditText
    private lateinit var currentDateTextView: TextView
    private lateinit var workoutButton: Button
    private lateinit var medicinesButton: Button
    private lateinit var happyButton: Button
    private lateinit var submitButton: Button
    private lateinit var viewHistoryButton: Button
    private lateinit var googleSignInButton: SignInButton // Google Sign-In button
    private lateinit var logoutButton: Button // Logout button

    // Firebase instances
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userId: String

    // Google Sign-In client
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001 // Request code for Google Sign-In

    // Calendar instance for date selection
    private val calendar = Calendar.getInstance()

    // State variables for the three daily input buttons
    private var workoutDone: Boolean? = null
    private var medicinesTaken: Boolean? = null
    private var happyStatus: Boolean? = null

    // SharedPreferences for local storage (e.g., last used username)
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "DailyTrackerPrefs"
    private val KEY_LAST_USERNAME = "last_username"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("APP_LIFECYCLE", "MainActivity onCreate started.")

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Initialize Firebase
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        Log.d("FIREBASE_INIT", "FirebaseApp initialized.")

        auth = Firebase.auth
        db = Firebase.firestore
        Log.d("FIREBASE_INIT", "FirebaseAuth and FirebaseFirestore instances obtained.")

        // Configure Google Sign-In to request the ID token for Firebase
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // IMPORTANT: Get this from google-services.json
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        Log.d("GOOGLE_SIGN_IN", "GoogleSignInClient initialized.")


        // Get references to UI elements
        userNameEditText = findViewById(R.id.userNameEditText)
        currentDateTextView = findViewById(R.id.currentDateTextView)
        workoutButton = findViewById(R.id.workoutButton)
        medicinesButton = findViewById(R.id.medicinesButton)
        happyButton = findViewById(R.id.happyButton)
        submitButton = findViewById(R.id.submitButton)
        viewHistoryButton = findViewById(R.id.viewHistoryButton)
        googleSignInButton = findViewById(R.id.googleSignInButton)
        logoutButton = findViewById(R.id.logoutButton)


        // Set initial date to today
        updateDateDisplay(calendar.time)

        // Load last used username from SharedPreferences
        val lastUsername = sharedPreferences.getString(KEY_LAST_USERNAME, "")
        if (!lastUsername.isNullOrEmpty()) {
            userNameEditText.setText(lastUsername)
        }

        // Handle date selection
        currentDateTextView.setOnClickListener {
            showDatePickerDialog()
        }

        // Handle button clicks for Workout, Medicines, Happy
        workoutButton.setOnClickListener { toggleButtonState(it as Button, "workout") }
        medicinesButton.setOnClickListener { toggleButtonState(it as Button, "medicines") }
        happyButton.setOnClickListener { toggleButtonState(it as Button, "happy") }

        // --- Google Sign-In Button Listener ---
        googleSignInButton.setOnClickListener {
            Log.d("GOOGLE_SIGN_IN", "Google Sign-In button clicked.")
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        // --- Logout Button Listener ---
        logoutButton.setOnClickListener {
            Log.d("AUTH_DEBUG", "Logout button clicked.")
            signOut()
        }

        // Initial check for authentication status
        updateUI(auth.currentUser)
    }

    /**
     * Handles the result from external activities, specifically Google Sign-In.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)!!
                Log.d("GOOGLE_SIGN_IN", "Google sign in successful. ID Token: ${account.idToken}")
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: com.google.android.gms.common.api.ApiException) {
                Log.w("GOOGLE_SIGN_IN", "Google sign in failed", e)
                Toast.makeText(this, "Google Sign-In failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
                updateUI(null) // Reset UI on failure
            }
        }
    }

    /**
     * Authenticates with Firebase using a Google ID Token.
     * Handles linking anonymous accounts if applicable.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = auth.currentUser

        if (currentUser != null && currentUser.isAnonymous) {
            // User is currently anonymous, link the Google account
            Log.d("AUTH_DEBUG", "Linking anonymous account (${currentUser.uid}) with Google credential.")
            currentUser.linkWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d("AUTH_DEBUG", "Account linking successful.")
                        Toast.makeText(this, "Account linked with Google!", Toast.LENGTH_SHORT).show()
                        updateUI(auth.currentUser) // Update UI with the linked user
                    } else {
                        Log.w("AUTH_DEBUG", "Account linking failed", task.exception)
                        Toast.makeText(this, "Account linking failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        updateUI(null) // Reset UI on failure
                    }
                }
        } else {
            // No anonymous user, or already signed in with a different provider, sign in directly
            Log.d("AUTH_DEBUG", "Signing in with Google credential directly.")
            auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d("AUTH_DEBUG", "Firebase sign in with Google successful.")
                        Toast.makeText(this, "Signed in with Google!", Toast.LENGTH_SHORT).show()
                        updateUI(auth.currentUser) // Update UI with the Google user
                    } else {
                        Log.w("AUTH_DEBUG", "Firebase sign in with Google failed", task.exception)
                        Toast.makeText(this, "Firebase Sign-In failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        updateUI(null) // Reset UI on failure
                    }
                }
        }
    }

    /**
     * Signs out the current Firebase user and Google account.
     */
    private fun signOut() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener(this) {
            Log.d("AUTH_DEBUG", "Google Sign-Out successful.")
            Toast.makeText(this, "Signed out.", Toast.LENGTH_SHORT).show()
            updateUI(null) // Reset UI after sign out
            // After signing out, we might want to sign in anonymously again automatically
            // to allow continued use of the app without explicit Google login.
            // This will generate a new anonymous UID.
            auth.signInAnonymously()
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d("AUTH_DEBUG", "Signed in anonymously after logout.")
                        updateUI(auth.currentUser)
                    } else {
                        Log.w("AUTH_DEBUG", "Anonymous sign-in failed after logout.", task.exception)
                    }
                }
        }
    }

    /**
     * Updates the UI based on the current Firebase user's authentication status.
     * @param user The current FirebaseUser, or null if signed out.
     */
    private fun updateUI(user: com.google.firebase.auth.FirebaseUser?) {
        if (user != null) {
            userId = user.uid
            Log.d("AUTH_STATUS", "UI updated. Current Firebase User ID: $userId")
            Log.d("AUTH_STATUS", "Is Anonymous: ${user.isAnonymous}")
            Log.d("AUTH_STATUS", "Email: ${user.email ?: "N/A"}")

            // Show logout button, hide sign-in button
            googleSignInButton.visibility = Button.GONE
            logoutButton.visibility = Button.VISIBLE

            // Enable main app functionality
            submitButton.isEnabled = true
            viewHistoryButton.isEnabled = true
            workoutButton.isEnabled = true
            medicinesButton.isEnabled = true
            happyButton.isEnabled = true

            // Load user data and daily entry for the current user/profile
            val currentUsername = userNameEditText.text.toString().trim()
            if (currentUsername.isNotEmpty()) {
                loadDailyEntry(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time), currentUsername)
            } else {
                resetButtonStates()
            }

            // Set up submit button listener (re-set in case UI was disabled)
            submitButton.setOnClickListener {
                Log.d("BUTTON_CLICK", "Submit button clicked.")
                saveDailyEntry()
            }

            // Set up view history button listener (re-set in case UI was disabled)
            viewHistoryButton.setOnClickListener {
                Log.d("BUTTON_CLICK", "View History button clicked.")
                val currentUsernameForHistory = userNameEditText.text.toString().trim()
                if (currentUsernameForHistory.isEmpty()) {
                    Toast.makeText(this, "Please enter a username to view history.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val intent = Intent(this, HistoryActivity::class.java)
                intent.putExtra("USER_ID", userId) // Pass userId
                intent.putExtra("PROFILE_NAME", currentUsernameForHistory) // Pass the selected username
                startActivity(intent)
            }

        } else {
            // No user signed in or signed out
            Log.d("AUTH_STATUS", "No Firebase user. Resetting UI.")
            // Hide logout button, show sign-in button
            googleSignInButton.visibility = Button.VISIBLE
            logoutButton.visibility = Button.GONE

            // Disable main app functionality
            submitButton.isEnabled = false
            viewHistoryButton.isEnabled = false
            workoutButton.isEnabled = false
            medicinesButton.isEnabled = false
            happyButton.isEnabled = false

            resetButtonStates() // Clear button colors
            userNameEditText.setText("") // Clear username
            sharedPreferences.edit().remove(KEY_LAST_USERNAME).apply() // Clear last username
        }
    }

    /**
     * Shows a DatePickerDialog to allow the user to select a date.
     */
    private fun showDatePickerDialog() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this,
            { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                updateDateDisplay(calendar.time)
                val currentUsername = userNameEditText.text.toString().trim()
                if (currentUsername.isNotEmpty()) {
                    loadDailyEntry(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time), currentUsername)
                } else {
                    resetButtonStates() // Reset if no username is entered
                }
            }, year, month, day)
        datePickerDialog.show()
    }

    /**
     * Updates the TextView with the selected date.
     * @param date The Date object to display.
     */
    private fun updateDateDisplay(date: Date) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        currentDateTextView.text = "Date: ${dateFormat.format(date)} (Tap to change)"
    }

    /**
     * Toggles the state (Yes/No) of a given button and updates its color.
     * @param button The button that was clicked.
     * @param type The type of entry ("workout", "medicines", "happy").
     */
    private fun toggleButtonState(button: Button, type: String) {
        val newState: Boolean? = when (type) {
            "workout" -> {
                workoutDone = when (workoutDone) {
                    true -> false // Yes -> No
                    false -> null // No -> Neutral
                    null -> true // Neutral -> Yes
                }
                workoutDone
            }
            "medicines" -> {
                medicinesTaken = when (medicinesTaken) {
                    true -> false
                    false -> null
                    null -> true
                }
                medicinesTaken
            }
            "happy" -> {
                happyStatus = when (happyStatus) {
                    true -> false
                    false -> null
                    null -> true
                }
                happyStatus
            }
            else -> null
        }
        updateButtonColor(button, newState)
    }

    /**
     * Updates the background color of a button based on its state (Yes/No/Neutral).
     * @param button The button to update.
     * @param state The boolean state (true for Yes, false for No, null for Neutral).
     */
    private fun updateButtonColor(button: Button, state: Boolean?) {
        val colorResId = when (state) {
            true -> R.color.green_button
            false -> R.color.red_button
            null -> R.color.neutral_button
        }
        button.backgroundTintList = ContextCompat.getColorStateList(this, colorResId)
    }

    /**
     * Resets the state of all three daily input buttons to neutral.
     */
    private fun resetButtonStates() {
        workoutDone = null
        medicinesTaken = null
        happyStatus = null
        updateButtonColor(workoutButton, workoutDone)
        updateButtonColor(medicinesButton, medicinesTaken)
        updateButtonColor(happyButton, happyStatus)
    }

    /**
     * Loads the daily entry for the currently selected date and updates button states.
     * @param date The date string (YYYY-MM-DD) for which to load the entry.
     * @param profileName The username profile to load data for.
     */
    private fun loadDailyEntry(date: String, profileName: String) {
        if (!::userId.isInitialized) {
            Log.e("FIRESTORE_DEBUG", "loadDailyEntry: userId not initialized. Cannot load daily entry.")
            return
        }
        if (profileName.isEmpty()) {
            Log.d("FIRESTORE_DEBUG", "loadDailyEntry: profileName is empty. Resetting buttons.")
            resetButtonStates()
            return
        }

        val docRef = db.collection("artifacts")
            .document(getString(R.string.app_id))
            .collection("users")
            .document(userId)
            .collection("profiles")
            .document(profileName)
            .collection("daily_tracker")
            .document(date)

        Log.d("FIRESTORE_DEBUG", "Attempting to load daily entry from path: ${docRef.path}")

        docRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    workoutDone = document.getBoolean("workout")
                    medicinesTaken = document.getBoolean("medicines")
                    happyStatus = document.getBoolean("happy")
                    Log.d("FIRESTORE_DEBUG", "Daily entry loaded for $date (Profile: $profileName): Workout=$workoutDone, Medicines=$medicinesTaken, Happy=$happyStatus")
                } else {
                    // Reset states if no entry for this date for this profile
                    workoutDone = null
                    medicinesTaken = null
                    happyStatus = null
                    Log.d("FIRESTORE_DEBUG", "No daily entry found for $date (Profile: $profileName). Resetting button states.")
                }
                // Update button colors based on loaded states
                updateButtonColor(workoutButton, workoutDone)
                updateButtonColor(medicinesButton, medicinesTaken)
                updateButtonColor(happyButton, happyStatus)
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE_DEBUG", "Error loading daily entry", e)
                Toast.makeText(this, "Error loading entry for $date: ${e.message}", Toast.LENGTH_SHORT).show()
                // Reset states on error
                resetButtonStates()
            }
    }

    /**
     * Saves the current daily entry (Workout, Medicines, Happy status) and user name to Firestore.
     */
    private fun saveDailyEntry() {
        if (!::userId.isInitialized) {
            Log.e("FIRESTORE_DEBUG", "saveDailyEntry: userId not initialized. Cannot save data.")
            Toast.makeText(this, "Error: User not authenticated. Please restart app.", Toast.LENGTH_LONG).show()
            return
        }

        val selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val profileName = userNameEditText.text.toString().trim()

        if (profileName.isEmpty()) {
            Toast.makeText(this, "Please enter a username before submitting.", Toast.LENGTH_SHORT).show()
            return
        }

        // Save the last used username to SharedPreferences
        sharedPreferences.edit().putString(KEY_LAST_USERNAME, profileName).apply()

        // Create or update the profile document itself (optional, but good for explicit profile management)
        val profileDocRef = db.collection("artifacts")
            .document(getString(R.string.app_id))
            .collection("users")
            .document(userId)
            .collection("profiles")
            .document(profileName)

        profileDocRef.set(hashMapOf("last_active_date" to selectedDate), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { Log.d("FIRESTORE_DEBUG", "Profile '$profileName' updated.") }
            .addOnFailureListener { e -> Log.w("FIRESTORE_DEBUG", "Error updating profile '$profileName'", e) }

        // Save daily tracker entry
        val entryData = hashMapOf<String, Any>()
        workoutDone?.let { entryData["workout"] = it }
        medicinesTaken?.let { entryData["medicines"] = it }
        happyStatus?.let { entryData["happy"] = it }

        if (entryData.isEmpty()) {
            Toast.makeText(this, "No data to submit for the selected date.", Toast.LENGTH_SHORT).show()
            return
        }

        val docRef = db.collection("artifacts")
            .document(getString(R.string.app_id))
            .collection("users")
            .document(userId)
            .collection("profiles")
            .document(profileName)
            .collection("daily_tracker")
            .document(selectedDate)

        Log.d("FIRESTORE_DEBUG", "Attempting to save daily entry to path: ${docRef.path}")
        docRef.set(entryData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("FIRESTORE_DEBUG", "Daily entry for $selectedDate (Profile: $profileName) successfully saved/updated.")
                Toast.makeText(this, "Entry for $selectedDate submitted for $profileName!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE_DEBUG", "Error saving daily entry", e)
                Toast.makeText(this, "Failed to submit entry: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
