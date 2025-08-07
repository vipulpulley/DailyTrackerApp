package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
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
import com.google.android.flexbox.FlexboxLayout // Import FlexboxLayout
import android.text.TextUtils

class MainActivity : AppCompatActivity() {

    // UI elements
    private lateinit var currentDateTextView: TextView
    private lateinit var dynamicButtonContainer: FlexboxLayout // Changed to FlexboxLayout
    private lateinit var submitButton: Button
    private lateinit var viewHistoryButton: Button
    private lateinit var logoutButton: Button

    // Firebase instances
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userId: String
    private lateinit var currentProfileName: String

    // Calendar instance for date selection
    private val calendar = Calendar.getInstance()

    // State variables for dynamic daily items
    private val itemStates = mutableMapOf<String, Boolean?>() // Map to store state of each item (ItemName -> State)
    private val customItemsList = mutableListOf<String>() // List of custom item names for the current profile

    // SharedPreferences for local storage
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "DailyTrackerPrefs"

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

        // Get references to UI elements
        currentDateTextView = findViewById(R.id.currentDateTextView)
        dynamicButtonContainer = findViewById(R.id.dynamicButtonContainer) // Reference the FlexboxLayout
        submitButton = findViewById(R.id.submitButton)
        viewHistoryButton = findViewById(R.id.viewHistoryButton)
        logoutButton = findViewById(R.id.logoutButton)

        // Set initial date to today
        updateDateDisplay(calendar.time)

        // Handle date selection
        currentDateTextView.setOnClickListener {
            showDatePickerDialog()
        }

        // --- Logout Button Listener ---
        logoutButton.setOnClickListener {
            Log.d("AUTH_DEBUG", "Logout button clicked from MainActivity.")
            signOutAndReturnToLogin()
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // No user is signed in, redirect to LoginActivity
            Log.d("AUTH_FLOW", "No user authenticated. Redirecting to LoginActivity.")
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // Finish MainActivity so user can't go back to it without logging in
        } else {
            // User is authenticated, proceed with main app functionality
            userId = currentUser.uid
            // Get the profile name passed from ProfileSelectionActivity
            currentProfileName = intent.getStringExtra("PROFILE_NAME") ?: run {
                Log.e("AUTH_FLOW", "Profile Name not passed to MainActivity!")
                Toast.makeText(this, "Error: Profile not selected. Please re-login.", Toast.LENGTH_LONG).show()
                // Redirect back to profile selection or login if profile name is missing
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
            Log.d("AUTH_FLOW", "User authenticated: $userId. Profile: $currentProfileName. Proceeding with MainActivity.")
            updateMainUIForAuthenticatedUser()
        }
    }

    /**
     * Updates the main UI elements and loads data once a user is authenticated.
     */
    private fun updateMainUIForAuthenticatedUser() {
        // Enable main app functionality
        submitButton.isEnabled = true
        viewHistoryButton.isEnabled = true
        logoutButton.visibility = Button.VISIBLE // Show logout button

        // Load custom items for the current profile
        loadCustomItemsForProfile()

        // Set up submit button listener
        submitButton.setOnClickListener {
            Log.d("BUTTON_CLICK", "Submit button clicked.")
            saveDailyEntry()
        }

        // Set up view history button listener
        viewHistoryButton.setOnClickListener {
            Log.d("BUTTON_CLICK", "View History button clicked.")
            val intent = Intent(this, HistoryActivity::class.java)
            intent.putExtra("USER_ID", userId) // Pass userId
            intent.putExtra("PROFILE_NAME", currentProfileName) // Pass the selected profile name
            startActivity(intent)
        }
    }

    /**
     * Loads the custom items (e.g., Workout, Medicines, Happy) for the current profile from Firestore.
     * Then, it dynamically creates buttons based on these items.
     */
    private fun loadCustomItemsForProfile() {
        val profileDocRef = db.collection("artifacts")
            .document(getString(R.string.app_id))
            .collection("users")
            .document(userId)
            .collection("profiles")
            .document(currentProfileName)

        Log.d("MAIN_ACTIVITY_ITEMS", "Loading custom items for profile: $currentProfileName from path: ${profileDocRef.path}")

        profileDocRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val items = document.get("custom_items") as? List<String>
                    if (items != null && items.isNotEmpty()) {
                        customItemsList.clear()
                        customItemsList.addAll(items)
                        Log.d("MAIN_ACTIVITY_ITEMS", "Custom items loaded: $customItemsList")
                        createDynamicButtons() // Create buttons based on loaded items
                        // Load current day's entry for these items
                        loadDailyEntry(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time), currentProfileName)
                    } else {
                        Log.d("MAIN_ACTIVITY_ITEMS", "No 'custom_items' field or it's empty. Using default/empty state.")
                        customItemsList.clear() // Clear any old items
                        resetButtonStates() // Reset all buttons to neutral
                        Toast.makeText(this, "No custom items found. Please add some in 'Manage Profiles'.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.d("MAIN_ACTIVITY_ITEMS", "Profile document does not exist. Cannot load custom items.")
                    customItemsList.clear()
                    resetButtonStates()
                    Toast.makeText(this, "Profile not found. Please select or create one.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Log.w("MAIN_ACTIVITY_ITEMS", "Error loading custom items for profile", e)
                Toast.makeText(this, "Error loading custom items: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Dynamically creates buttons in the dynamicButtonContainer based on customItemsList.
     */
    private fun createDynamicButtons() {
        dynamicButtonContainer.removeAllViews() // Clear existing buttons
        itemStates.clear() // Clear previous states

        for (itemName in customItemsList) {
            val button = Button(this)
            // Use FlexboxLayout.LayoutParams for buttons inside FlexboxLayout
            val params = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT, // Width will wrap content
                FlexboxLayout.LayoutParams.WRAP_CONTENT // Height will wrap content
            ).apply {
                // Add margins for spacing between buttons
                setMargins(
                    resources.getDimensionPixelSize(R.dimen.button_margin_horizontal),
                    resources.getDimensionPixelSize(R.dimen.button_margin_vertical),
                    resources.getDimensionPixelSize(R.dimen.button_margin_horizontal),
                    resources.getDimensionPixelSize(R.dimen.button_margin_vertical)
                )
            }
            button.layoutParams = params

            button.text = itemName
            button.setOnClickListener { toggleButtonState(button, itemName) }
            // Ensure single line and no ellipsize for buttons
            button.setSingleLine(true)
            button.ellipsize = TextUtils.TruncateAt.END // Add ellipsis if text is too long for the button itself
            button.backgroundTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.neutral_button)

            dynamicButtonContainer.addView(button)
            itemStates[itemName] = null // Initialize state for each new button
        }
    }

    /**
     * Toggles the state (Yes/No/Neutral) of a dynamically created button and updates its color.
     * @param button The button that was clicked.
     * @param itemName The name of the custom item associated with the button.
     */
    private fun toggleButtonState(button: Button, itemName: String) {
        val currentState = itemStates[itemName]
        val newState: Boolean? = when (currentState) {
            true -> false // Yes -> No
            false -> null // No -> Neutral
            null -> true // Neutral -> Yes
        }
        itemStates[itemName] = newState // Update the state in the map
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
     * Resets the state of all dynamic buttons to neutral.
     */
    private fun resetButtonStates() {
        itemStates.clear() // Clear all states
        for (i in 0 until dynamicButtonContainer.childCount) {
            val button = dynamicButtonContainer.getChildAt(i) as Button
            updateButtonColor(button, null) // Set to neutral
        }
    }

    /**
     * Loads the daily entry for the currently selected date and updates dynamic button states.
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
                // Reset all item states to null first
                itemStates.keys.forEach { key -> itemStates[key] = null }

                if (document.exists()) {
                    Log.d("FIRESTORE_DEBUG", "Document exists for $date. Loading item states.")
                    // Iterate through custom items and load their states
                    for (itemName in customItemsList) {
                        val state = document.getBoolean(itemName)
                        itemStates[itemName] = state // Store the loaded state (true, false, or null)
                    }
                } else {
                    Log.d("FIRESTORE_DEBUG", "No daily entry found for $date (Profile: $profileName). Resetting item states.")
                }
                // Update button colors based on loaded/reset states
                for (i in 0 until dynamicButtonContainer.childCount) {
                    val button = dynamicButtonContainer.getChildAt(i) as Button
                    val itemName = button.text.toString()
                    updateButtonColor(button, itemStates[itemName])
                }
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE_DEBUG", "Error loading daily entry", e)
                Toast.makeText(this, "Error loading entry for $date: ${e.message}", Toast.LENGTH_SHORT).show()
                resetButtonStates() // Reset states on error
            }
    }

    /**
     * Saves the current daily entry (dynamic item states) to Firestore.
     */
    private fun saveDailyEntry() {
        if (!::userId.isInitialized) {
            Log.e("FIRESTORE_DEBUG", "saveDailyEntry: userId not initialized. Cannot save data.")
            Toast.makeText(this, "Error: User not authenticated. Please restart app.", Toast.LENGTH_LONG).show()
            return
        }

        val selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val profileName = currentProfileName

        if (profileName.isEmpty()) {
            Toast.makeText(this, "Error: Profile name not set. Cannot submit.", Toast.LENGTH_SHORT).show()
            return
        }

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
        var hasDataToSave = false
        for ((itemName, state) in itemStates) {
            state?.let { // Only save if state is true or false (not null)
                entryData[itemName] = it
                hasDataToSave = true
            }
        }

        if (!hasDataToSave) {
            Toast.makeText(this, "No data to submit for the selected date. All items are neutral.", Toast.LENGTH_SHORT).show()
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

        Log.d("FIRESTORE_DEBUG", "Attempting to save daily entry to path: ${docRef.path} with data: $entryData")
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

    /**
     * Signs out the current Firebase user and returns to LoginActivity.
     */
    private fun signOutAndReturnToLogin() {
        auth.signOut()
        Log.d("AUTH_DEBUG", "Firebase Sign-Out successful. Redirecting to LoginActivity.")
        Toast.makeText(this, "Signed out.", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // Finish MainActivity
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
                loadDailyEntry(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time), currentProfileName)
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
}
