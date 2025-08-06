package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
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

    // Firebase instances
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userId: String

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

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
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

        // Get references to UI elements
        userNameEditText = findViewById(R.id.userNameEditText)
        currentDateTextView = findViewById(R.id.currentDateTextView)
        workoutButton = findViewById(R.id.workoutButton)
        medicinesButton = findViewById(R.id.medicinesButton)
        happyButton = findViewById(R.id.happyButton)
        submitButton = findViewById(R.id.submitButton)
        viewHistoryButton = findViewById(R.id.viewHistoryButton)

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

        // --- Authentication Logic ---
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is already signed in (persisted session)
            userId = currentUser.uid
            Log.d("AUTH_DEBUG", "User already signed in. UID: $userId")
            Toast.makeText(this, "Welcome back! User ID: $userId", Toast.LENGTH_LONG).show()
            onAuthSuccess() // Proceed with app setup
        } else {
            // No user signed in, proceed with anonymous sign-in
            Log.d("AUTH_DEBUG", "No existing user found. Signing in anonymously...")
            auth.signInAnonymously()
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d("AUTH_DEBUG", "signInAnonymously:success")
                        val user = auth.currentUser
                        userId = user?.uid ?: "anonymous_user_${System.currentTimeMillis()}" // Fallback if UID is null
                        Log.d("AUTH_DEBUG", "New anonymous user signed in. UID: $userId")
                        Toast.makeText(this, "Firebase authenticated successfully! User ID: $userId", Toast.LENGTH_LONG).show()
                        onAuthSuccess() // Proceed with app setup
                    } else {
                        Log.w("AUTH_DEBUG", "signInAnonymously:failure", task.exception)
                        Toast.makeText(this, "Firebase Auth failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        // Disable buttons if authentication fails
                        submitButton.isEnabled = false
                        viewHistoryButton.isEnabled = false
                        workoutButton.isEnabled = false
                        medicinesButton.isEnabled = false
                        happyButton.isEnabled = false
                    }
                }
        }
    }

    /**
     * This function is called once Firebase authentication is successful.
     * It handles loading user data, setting up listeners, and enabling UI.
     */
    private fun onAuthSuccess() {
        Log.d("AUTH_DEBUG", "onAuthSuccess called for UID: $userId")

        // Load daily entry for the current date and username
        val currentUsername = userNameEditText.text.toString().trim()
        if (currentUsername.isNotEmpty()) {
            loadDailyEntry(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time), currentUsername)
        } else {
            // If no username is pre-filled, reset buttons to neutral
            resetButtonStates()
        }

        // Set up submit button listener
        submitButton.setOnClickListener {
            Log.d("BUTTON_CLICK", "Submit button clicked.")
            saveDailyEntry()
        }

        // Set up view history button listener
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

        // Add a listener to userNameEditText to reload data when username changes
        userNameEditText.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) { // When focus leaves the EditText
                val newUsername = userNameEditText.text.toString().trim()
                val oldUsername = sharedPreferences.getString(KEY_LAST_USERNAME, "")
                if (newUsername.isNotEmpty() && newUsername != oldUsername) {
                    // Save new username as last used
                    sharedPreferences.edit().putString(KEY_LAST_USERNAME, newUsername).apply()
                    // Reload data for the new username and current date
                    loadDailyEntry(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time), newUsername)
                } else if (newUsername.isEmpty() && oldUsername?.isNotEmpty() == true) {
                    // If username is cleared, reset buttons
                    sharedPreferences.edit().remove(KEY_LAST_USERNAME).apply()
                    resetButtonStates()
                }
            }
        }
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
            .collection("profiles") // New collection
            .document(profileName) // New document for the profile
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
            .collection("profiles") // New collection
            .document(profileName) // New document for the profile
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
