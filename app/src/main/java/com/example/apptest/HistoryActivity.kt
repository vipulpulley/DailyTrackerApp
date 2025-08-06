package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat // Import for ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldPath // Import FieldPath
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class HistoryActivity : AppCompatActivity() {

    private lateinit var historyTableLayout: TableLayout // Changed to TableLayout
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var userId: String
    private lateinit var profileName: String

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

        historyTableLayout = findViewById(R.id.historyTableLayout) // Get reference to TableLayout

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


        // Set up the Firestore listener to display history
        setupFirestoreListener()
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
                        noEntriesRow.addView(noEntriesTextView, TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT, 4f)) // Span all columns
                        historyTableLayout.addView(noEntriesRow)
                    } else {
                        for (doc in snapshots.documents) {
                            val date = doc.id // Document ID is the date (e.g., "2025-08-06")
                            val workout = doc.getBoolean("workout")
                            val medicines = doc.getBoolean("medicines")
                            val happy = doc.getBoolean("happy")

                            val tableRow = TableRow(this)
                            tableRow.layoutParams = TableLayout.LayoutParams(
                                TableLayout.LayoutParams.MATCH_PARENT,
                                TableLayout.LayoutParams.WRAP_CONTENT
                            )
                            tableRow.setPadding(0, 4, 0, 4) // Padding for the row

                            // Date TextView
                            val dateTextView = TextView(this)
                            dateTextView.text = date
                            dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            dateTextView.gravity = Gravity.CENTER
                            dateTextView.setPadding(8, 8, 8, 8)
                            tableRow.addView(dateTextView, TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2f)) // Weight 2 for date

                            // Workout TextView
                            val workoutTextView = TextView(this)
                            workoutTextView.text = if (workout == true) "Yes" else if (workout == false) "No" else "-"
                            workoutTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            workoutTextView.gravity = Gravity.CENTER
                            workoutTextView.setPadding(8, 8, 8, 8)
                            workoutTextView.setBackgroundColor(ContextCompat.getColor(this, getButtonColorResId(workout)))
                            tableRow.addView(workoutTextView, TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f))

                            // Medicines TextView
                            val medicinesTextView = TextView(this)
                            medicinesTextView.text = if (medicines == true) "Yes" else if (medicines == false) "No" else "-"
                            medicinesTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            medicinesTextView.gravity = Gravity.CENTER
                            medicinesTextView.setPadding(8, 8, 8, 8)
                            medicinesTextView.setBackgroundColor(ContextCompat.getColor(this, getButtonColorResId(medicines)))
                            tableRow.addView(medicinesTextView, TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f))

                            // Happy TextView
                            val happyTextView = TextView(this)
                            happyTextView.text = if (happy == true) "Yes" else if (happy == false) "No" else "-"
                            happyTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            happyTextView.gravity = Gravity.CENTER
                            happyTextView.setPadding(8, 8, 8, 8)
                            happyTextView.setBackgroundColor(ContextCompat.getColor(this, getButtonColorResId(happy)))
                            tableRow.addView(happyTextView, TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f))

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
                    noEntriesRow.addView(noEntriesTextView, TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT, 4f)) // Span all columns
                    historyTableLayout.addView(noEntriesRow)
                }
            }
    }
}
