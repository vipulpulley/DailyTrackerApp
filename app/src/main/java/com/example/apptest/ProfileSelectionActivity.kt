package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ProfileSelectionActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userId: String
    private lateinit var profilesRecyclerView: RecyclerView
    private lateinit var addProfileButton: Button
    private lateinit var logoutButton: Button
    private lateinit var profileAdapter: ProfileAdapter
    private val profileList = mutableListOf<String>()

    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "DailyTrackerPrefs"
    private val KEY_LAST_USERNAME = "last_username"

    // Google Sign-In client for logout
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_selection)

        Log.d("PROFILE_SELECT_ACTIVITY", "ProfileSelectionActivity onCreate started.")

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Initialize Firebase
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        auth = Firebase.auth
        db = Firebase.firestore

        // Configure Google Sign-In for logout
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Get UI references
        profilesRecyclerView = findViewById(R.id.profilesRecyclerView)
        addProfileButton = findViewById(R.id.addProfileButton)
        logoutButton = findViewById(R.id.logoutButton)

        // Get userId from authenticated user
        val currentUser = auth.currentUser
        if (currentUser != null) {
            userId = currentUser.uid
            Log.d("PROFILE_SELECT_ACTIVITY", "Authenticated User ID: $userId")
        } else {
            Log.e("PROFILE_SELECT_ACTIVITY", "No user authenticated. Redirecting to LoginActivity.")
            Toast.makeText(this, "Authentication required.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Setup RecyclerView
        profileAdapter = ProfileAdapter(
            profileList,
            onItemClick = { selectedProfile ->
                // Handle profile selection (navigate to MainActivity)
                Log.d("PROFILE_SELECT_ACTIVITY", "Profile selected: $selectedProfile")
                sharedPreferences.edit().putString(KEY_LAST_USERNAME, selectedProfile).apply() // Save selected profile
                navigateToMainActivity(selectedProfile)
            },
            onManageItemsClick = { profileToManage ->
                // Handle manage items click (navigate to ManageItemsActivity)
                Log.d("PROFILE_SELECT_ACTIVITY", "Manage Items clicked for profile: $profileToManage")
                navigateToManageItemsActivity(profileToManage)
            }
        )
        profilesRecyclerView.adapter = profileAdapter

        // Load profiles from Firestore
        loadProfiles()

        // Set up Add Profile button
        addProfileButton.setOnClickListener {
            showAddProfileDialog()
        }

        // Set up Logout button (top right)
        logoutButton.setOnClickListener {
            signOutAndReturnToLogin()
        }
    }

    /**
     * Loads existing profiles from Firestore for the current user.
     */
    private fun loadProfiles() {
        val profilesCollectionRef = db.collection("artifacts")
            .document(getString(R.string.app_id))
            .collection("users")
            .document(userId)
            .collection("profiles")

        Log.d("PROFILE_SELECT_ACTIVITY", "Loading profiles from path: ${profilesCollectionRef.path}")

        profilesCollectionRef.get()
            .addOnSuccessListener { querySnapshot ->
                profileList.clear() // Clear existing list
                for (document in querySnapshot.documents) {
                    profileList.add(document.id) // Document ID is the profile name
                }
                profileAdapter.notifyDataSetChanged() // Notify adapter of data change
                Log.d("PROFILE_SELECT_ACTIVITY", "Profiles loaded: ${profileList.size}")

                if (profileList.isEmpty()) {
                    Log.d("PROFILE_SELECT_ACTIVITY", "No profiles found. Prompting to add new.")
                    Toast.makeText(this, "No profiles found. Add a new one!", Toast.LENGTH_LONG).show()
                    showAddProfileDialog() // Automatically prompt to add if no profiles
                } else {
                    Log.d("PROFILE_SELECT_ACTIVITY", "Profiles exist. Displaying list for selection.")
                }
            }
            .addOnFailureListener { e ->
                Log.w("PROFILE_SELECT_ACTIVITY", "Error loading profiles", e)
                Toast.makeText(this, "Error loading profiles: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Shows a dialog to add a new profile.
     */
    private fun showAddProfileDialog() {
        val inputEditText = EditText(this)
        inputEditText.hint = "Enter new profile name"

        AlertDialog.Builder(this)
            .setTitle("Add New Profile")
            .setView(inputEditText)
            .setPositiveButton("Add") { dialog, _ ->
                val newProfileName = inputEditText.text.toString().trim()
                if (newProfileName.isNotEmpty()) {
                    if (profileList.contains(newProfileName)) {
                        Toast.makeText(this, "Profile '$newProfileName' already exists.", Toast.LENGTH_SHORT).show()
                    } else {
                        addProfile(newProfileName)
                    }
                } else {
                    Toast.makeText(this, "Profile name cannot be empty.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    /**
     * Adds a new profile to Firestore and updates the UI.
     * @param profileName The name of the new profile.
     */
    private fun addProfile(profileName: String) {
        val profileDocRef = db.collection("artifacts")
            .document(getString(R.string.app_id))
            .collection("users")
            .document(userId)
            .collection("profiles")
            .document(profileName)

        // Create a dummy field to ensure the document exists and to store default items
        val initialData = hashMapOf(
            "created_at" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "custom_items" to listOf("Workout", "Medicines", "Happy") // Initialize with default items
        )

        profileDocRef.set(initialData)
            .addOnSuccessListener {
                Log.d("PROFILE_SELECT_ACTIVITY", "Profile '$profileName' added to Firestore with default items.")
                Toast.makeText(this, "Profile '$profileName' created!", Toast.LENGTH_SHORT).show()
                profileList.add(profileName) // Add to local list
                profileAdapter.notifyItemInserted(profileList.size - 1) // Notify adapter
            }
            .addOnFailureListener { e ->
                Log.w("PROFILE_SELECT_ACTIVITY", "Error adding profile", e)
                Toast.makeText(this, "Error adding profile: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Navigates to MainActivity with the selected profile name.
     * @param selectedProfile The profile name to pass to MainActivity.
     */
    private fun navigateToMainActivity(selectedProfile: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("PROFILE_NAME", selectedProfile)
        startActivity(intent)
    }

    /**
     * Navigates to ManageItemsActivity for the selected profile.
     * @param profileToManage The profile name whose items are to be managed.
     */
    private fun navigateToManageItemsActivity(profileToManage: String) {
        val intent = Intent(this, ManageItemsActivity::class.java)
        intent.putExtra("USER_ID", userId)
        intent.putExtra("PROFILE_NAME", profileToManage)
        startActivity(intent)
    }

    /**
     * Signs out the current Firebase user and Google account, then returns to LoginActivity.
     */
    private fun signOutAndReturnToLogin() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener(this) {
            Log.d("PROFILE_SELECT_ACTIVITY", "Google Sign-Out successful.")
            Toast.makeText(this, "Signed out.", Toast.LENGTH_SHORT).show()
            // Clear last used username from SharedPreferences
            sharedPreferences.edit().remove(KEY_LAST_USERNAME).apply()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // Finish ProfileSelectionActivity when logging out
        }
    }
}