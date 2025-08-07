package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import android.graphics.Typeface
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn

class HistoryActivity : AppCompatActivity() {

    private lateinit var historyTableLayout: TableLayout
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var userId: String
    private lateinit var profileName: String
    private lateinit var logoutButton: Button

    private val customItemsList = mutableListOf<String>() // To store custom items for headers

    // Google Sign-In client for logout
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        Log.d("HISTORY_DEBUG", "HistoryActivity onCreate started.")

        // Initialize Firebase (ensure it's initialized if not already)
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
            Log.d("HISTORY_DEBUG", "FirebaseApp initialized in HistoryActivity.")
        } else {
            Log.d("HISTORY_DEBUG", "FirebaseApp already initialized.")
        }

        auth = Firebase.auth
        db = Firebase.firestore
        Log.d("HISTORY_DEBUG", "FirebaseAuth and FirebaseFirestore instances obtained in HistoryActivity.")

        // Configure Google Sign-In for logout
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        historyTableLayout = findViewById(R.id.historyTableLayout)
        logoutButton = findViewById(R.id.logoutButton)

        // Get the userId passed from MainActivity
        userId = intent.getStringExtra("USER_ID") ?: run {
            Log.e("HISTORY_DEBUG", "User ID not passed to HistoryActivity!")
            Toast.makeText(this, "Error: User ID not found.", Toast.LENGTH_LONG).show()
            finish() // Close this activity if userId is missing
            return
        }
        Log.d("HISTORY_DEBUG", "Received USER_ID: $userId")


        // Get the profileName passed from MainActivity
        profileName = intent.getStringExtra("PROFILE_NAME") ?: run {
            Log.e("HISTORY_DEBUG", "Profile Name not passed to HistoryActivity!")
            Toast.makeText(this, "Error: Profile Name not found.", Toast.LENGTH_LONG).show()
            finish() // Close this activity if profileName is missing
            return
        }
        Log.d("HISTORY_DEBUG", "Received PROFILE_NAME: $profileName")


        // Update the title to reflect the profile
        val historyTitleTextView: TextView = findViewById(R.id.historyTitleTextView)
        historyTitleTextView.text = "$profileName's Daily Entries"
        Log.d("HISTORY_DEBUG", "History title set to: ${historyTitleTextView.text}")


        // Set up Logout button
        logoutButton.setOnClickListener {
            signOutAndReturnToLogin()
        }

        // Load custom items first, then setup Firestore listener
        loadCustomItemsForHistory()
    }

    /**
     * Helper function to get the color resource ID based on boolean state.
     * Matches the logic used for buttons in MainActivity.
     */
    private fun getButtonColorResId(state: Boolean?): Int {
        return when (state) {
            true -> R.color.green_button
            false -> R.color.red_button
            null -> R.color.neutral_button // Use neutral for unset/null
        }
    }

    /**
     * Loads the custom items (e.g., Workout, Medicines, Happy) for the current profile from Firestore.
     * Then, it sets up the Firestore listener for daily entries.
     */
    private fun loadCustomItemsForHistory() {
        val profileDocRef = db.collection("artifacts")
            .document(getString(R.string.app_id))
            .collection("users")
            .document(userId)
            .collection("profiles")
            .document(profileName)

        Log.d("HISTORY_DEBUG", "Loading custom items for profile: $profileName from path: ${profileDocRef.path}")

        profileDocRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val items = document.get("custom_items") as? List<String>
                    if (items != null && items.isNotEmpty()) {
                        customItemsList.clear()
                        customItemsList.addAll(items)
                        Log.d("HISTORY_DEBUG", "Custom items loaded: $customItemsList")
                        createTableHeader() // Create headers based on loaded items
                        setupFirestoreListener() // Setup listener after headers are ready
                    } else {
                        Log.d("HISTORY_DEBUG", "No 'custom_items' field or it's empty. Displaying empty history.")
                        customItemsList.clear() // Ensure list is clear if no items found
                        createTableHeader() // Still create header (Date only)
                        setupFirestoreListener() // Setup listener (will show no entries)
                    }
                } else {
                    Log.d("HISTORY_DEBUG", "Profile document does not exist. Cannot load custom items for history.")
                    customItemsList.clear() // Ensure list is clear if profile doesn't exist
                    createTableHeader() // Still create header (Date only)
                    setupFirestoreListener() // Setup listener (will show no entries)
                }
            }
            .addOnFailureListener { e ->
                Log.w("HISTORY_DEBUG", "Error loading custom items for history", e)
                Toast.makeText(this, "Error loading custom items for history: ${e.message}", Toast.LENGTH_LONG).show()
                customItemsList.clear() // Ensure list is clear on error
                createTableHeader() // Fallback to just date header on error
                setupFirestoreListener() // Setup listener
            }
    }

    /**
     * Dynamically creates the table header row based on customItemsList.
     */
    private fun createTableHeader() {
        // Ensure only one header row exists
        if (historyTableLayout.childCount > 0) {
            historyTableLayout.removeViews(0, historyTableLayout.childCount) // Clear all views including old header
        }

        val headerRow = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.light_gray_header)) // Use a light gray for header
            setPadding(8, 8, 8, 8)
        }

        // Date Header
        val dateHeader = TextView(this).apply {
            // Use WRAP_CONTENT for width to allow expansion, no weight here
            layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
            text = "Date"
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        headerRow.addView(dateHeader)

        // Custom Item Headers
        for (itemName in customItemsList) {
            val itemHeader = TextView(this).apply {
                // Use WRAP_CONTENT for width to allow expansion, no weight here
                layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
                text = "$itemName " // FIX: Added a space after the item name
                setTypeface(null, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.black))
            }
            headerRow.addView(itemHeader)
        }
        historyTableLayout.addView(headerRow)
    }

    /**
     * Sets up a real-time listener for daily entries from Firestore for the current user and profile.
     * Updates the history display whenever data changes.
     */
    private fun setupFirestoreListener() {
        val collectionRef = db.collection("artifacts")
            .document(getString(R.string.app_id)) // Use the app_id from strings.xml
            .collection("users")
            .document(userId)
            .collection("profiles") // New collection
            .document(profileName) // New document for the profile
            .collection("daily_tracker")

        Log.d("HISTORY_DEBUG", "Setting up Firestore listener for path: ${collectionRef.path}")

        // Order entries by document ID in descending order (most recent first)
        collectionRef.orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("HISTORY_DEBUG", "Firestore listen failed. Error: ${e.message}", e)
                    Toast.makeText(this, "Failed to load history: ${e.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    Log.d("HISTORY_DEBUG", "Received ${snapshots.documents.size} documents from Firestore.")
                    // Remove all rows except the header row (index 0)
                    if (historyTableLayout.childCount > 1) {
                        historyTableLayout.removeViews(1, historyTableLayout.childCount - 1)
                    }

                    if (snapshots.documents.isEmpty()) {
                        Log.d("HISTORY_DEBUG", "No documents found for this profile. Displaying 'No entries' message.")
                        val noEntriesRow = TableRow(this)
                        val noEntriesTextView = TextView(this)
                        noEntriesTextView.text = "No past entries yet for $profileName."
                        noEntriesTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        noEntriesTextView.gravity = Gravity.CENTER
                        noEntriesTextView.setPadding(8, 8, 8, 8)
                        noEntriesTextView.setTextColor(ContextCompat.getColor(this, R.color.black))
                        val spanCount = 1 + customItemsList.size // 1 for date, plus number of custom items
                        noEntriesRow.addView(noEntriesTextView, TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT, spanCount.toFloat()))
                        historyTableLayout.addView(noEntriesRow)
                    } else {
                        for (doc in snapshots.documents) {
                            val date = doc.id // Document ID is the date (e.g., "2025-08-06")

                            val tableRow = TableRow(this)
                            tableRow.layoutParams = TableLayout.LayoutParams(
                                TableLayout.LayoutParams.MATCH_PARENT,
                                TableLayout.LayoutParams.WRAP_CONTENT
                            )
                            tableRow.setPadding(0, 4, 0, 4)

                            // Date TextView
                            val dateTextView = TextView(this)
                            dateTextView.text = date
                            dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            dateTextView.gravity = Gravity.CENTER
                            dateTextView.setPadding(8, 8, 8, 8)
                            dateTextView.setTextColor(ContextCompat.getColor(this, R.color.black))
                            tableRow.addView(dateTextView, TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT))

                            // Dynamically add TextViews for each custom item
                            for (itemName in customItemsList) {
                                val itemState = doc.getBoolean(itemName) // Get state for this item
                                val itemTextView = TextView(this)
                                itemTextView.text = if (itemState == true) "Yes" else if (itemState == false) "No" else "-"
                                itemTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                                itemTextView.gravity = Gravity.CENTER
                                itemTextView.setPadding(8, 8, 8, 8)
                                itemTextView.setBackgroundColor(ContextCompat.getColor(this, getButtonColorResId(itemState)))
                                itemTextView.setTextColor(ContextCompat.getColor(this, R.color.white))
                                tableRow.addView(itemTextView, TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT))
                            }
                            historyTableLayout.addView(tableRow)
                        }
                    }
                } else {
                    Log.d("HISTORY_DEBUG", "Current data: null (snapshots object is null). Displaying 'No entries' message.")
                    val noEntriesRow = TableRow(this)
                    val noEntriesTextView = TextView(this)
                    noEntriesTextView.text = "No past entries yet for $profileName."
                    noEntriesTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    noEntriesTextView.gravity = Gravity.CENTER
                    noEntriesTextView.setPadding(8, 8, 8, 8)
                    noEntriesTextView.setTextColor(ContextCompat.getColor(this, R.color.black))
                    val spanCount = 1 + customItemsList.size
                    noEntriesRow.addView(noEntriesTextView, TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT, spanCount.toFloat()))
                    historyTableLayout.addView(noEntriesRow)
                }
            }
    }

    /**
     * Signs out the current Firebase user and Google account, then returns to LoginActivity.
     */
    private fun signOutAndReturnToLogin() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener(this) {
            Log.d("HISTORY_DEBUG", "Google Sign-Out successful.")
            Toast.makeText(this, "Signed out.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // Finish HistoryActivity when logging out
        }
    }
}
