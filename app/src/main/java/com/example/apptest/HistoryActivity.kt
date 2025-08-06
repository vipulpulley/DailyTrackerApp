package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FieldPath // Import FieldPath

class HistoryActivity : AppCompatActivity() {

    private lateinit var historyEntriesLayout: LinearLayout
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

        historyEntriesLayout = findViewById(R.id.historyEntriesLayout)

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

        // --- THE FIX IS HERE: Order by document ID instead of a non-existent 'date' field ---
        collectionRef.orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("HISTORY_DEBUG", "Firestore listen failed. Error: ${e.message}", e)
                    Toast.makeText(this, "Failed to load history: ${e.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    Log.d("HISTORY_DEBUG", "Received ${snapshots.documents.size} documents from Firestore.")
                    historyEntriesLayout.removeAllViews() // Clear all existing views

                    if (snapshots.documents.isEmpty()) {
                        Log.d("HISTORY_DEBUG", "No documents found for this profile. Displaying 'No entries' message.")
                        val noEntriesTextView = TextView(this)
                        noEntriesTextView.text = "No past entries yet for $profileName."
                        noEntriesTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        historyEntriesLayout.addView(noEntriesTextView)
                    } else {
                        for (doc in snapshots.documents) {
                            val date = doc.id // Document ID is the date (e.g., "2025-08-06")
                            val workout = doc.getBoolean("workout") ?: false
                            val medicines = doc.getBoolean("medicines") ?: false
                            val happy = doc.getBoolean("happy") ?: false

                            val entryText = "$date:\n" +
                                    "  Workout: ${if (workout) "Yes" else "No"}\n" +
                                    "  Medicines: ${if (medicines) "Yes" else "No"}\n" +
                                    "  Happy: ${if (happy) "Yes" else "No"}"

                            Log.d("HISTORY_DEBUG", "Adding entry: $entryText")
                            val entryTextView = TextView(this)
                            entryTextView.text = entryText
                            entryTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                            entryTextView.setPadding(0, 8, 0, 8) // Add some padding between entries
                            historyEntriesLayout.addView(entryTextView)
                        }
                    }
                } else {
                    Log.d("HISTORY_DEBUG", "Current data: null (snapshots object is null). Displaying 'No entries' message.")
                    val noEntriesTextView = TextView(this)
                    noEntriesTextView.text = "No past entries yet for $profileName."
                    noEntriesTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    historyEntriesLayout.addView(noEntriesTextView)
                }
            }
    }
}
