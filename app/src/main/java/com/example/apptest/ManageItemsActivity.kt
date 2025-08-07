package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ManageItemsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userId: String
    private lateinit var profileName: String
    private lateinit var customItemsRecyclerView: RecyclerView
    private lateinit var addItemButton: Button
    private lateinit var customItemAdapter: CustomItemAdapter
    private val customItemsList = mutableListOf<String>() // List to hold custom items

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_items)

        Log.d("MANAGE_ITEMS_ACTIVITY", "ManageItemsActivity onCreate started.")

        // Initialize Firebase
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        auth = Firebase.auth
        db = Firebase.firestore

        // Get UI references
        customItemsRecyclerView = findViewById(R.id.customItemsRecyclerView)
        addItemButton = findViewById(R.id.addItemButton)

        // Get userId and profileName from intent
        userId = intent.getStringExtra("USER_ID") ?: run {
            Log.e("MANAGE_ITEMS_ACTIVITY", "User ID not passed to ManageItemsActivity!")
            Toast.makeText(this, "Error: User ID not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        profileName = intent.getStringExtra("PROFILE_NAME") ?: run {
            Log.e("MANAGE_ITEMS_ACTIVITY", "Profile Name not passed to ManageItemsActivity!")
            Toast.makeText(this, "Error: Profile Name not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Log.d("MANAGE_ITEMS_ACTIVITY", "Managing items for UserID: $userId, Profile: $profileName")

        // Setup RecyclerView with adapter
        customItemAdapter = CustomItemAdapter(customItemsList) { itemToDelete ->
            // Callback for delete button click
            confirmDeleteItem(itemToDelete)
        }
        customItemsRecyclerView.adapter = customItemAdapter

        // Load custom items for this profile
        loadCustomItems()

        // Set up Add Item button
        addItemButton.setOnClickListener {
            showAddItemDialog()
        }
    }

    /**
     * Loads custom items from Firestore for the current profile.
     */
    private fun loadCustomItems() {
        val profileDocRef = db.collection("artifacts")
            .document(getString(R.string.app_id))
            .collection("users")
            .document(userId)
            .collection("profiles")
            .document(profileName)

        Log.d("MANAGE_ITEMS_ACTIVITY", "Loading custom items from path: ${profileDocRef.path}")

        profileDocRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val items = document.get("custom_items") as? List<String>
                    if (items != null) {
                        customItemsList.clear()
                        customItemsList.addAll(items) // Update the class-level list
                        customItemAdapter.notifyDataSetChanged() // Notify adapter after updating the list
                        Log.d("MANAGE_ITEMS_ACTIVITY", "Custom items loaded: $items")
                    } else {
                        Log.d("MANAGE_ITEMS_ACTIVITY", "No 'custom_items' field or it's not a list of strings. Initializing default items.")
                        initializeDefaultItems()
                    }
                } else {
                    Log.d("MANAGE_ITEMS_ACTIVITY", "Profile document does not exist. Initializing default items.")
                    initializeDefaultItems()
                }
            }
            .addOnFailureListener { e ->
                Log.w("MANAGE_ITEMS_ACTIVITY", "Error loading custom items", e)
                Toast.makeText(this, "Error loading items: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Initializes a profile with default items if no custom items are found.
     */
    private fun initializeDefaultItems() {
        val defaultItems = listOf("Workout", "Medicines", "Happy")
        customItemsList.clear()
        customItemsList.addAll(defaultItems) // Update the class-level list
        updateCustomItemsInFirestore() // Save to Firestore and update adapter
    }

    /**
     * Shows a dialog to add a new custom item.
     */
    private fun showAddItemDialog() {
        val inputEditText = EditText(this)
        inputEditText.hint = "Enter new item name"

        AlertDialog.Builder(this)
            .setTitle("Add New Daily Item")
            .setView(inputEditText)
            .setPositiveButton("Add") { dialog, _ ->
                val newItemName = inputEditText.text.toString().trim()
                if (newItemName.isNotEmpty()) {
                    if (customItemsList.contains(newItemName)) {
                        Toast.makeText(this, "Item '$newItemName' already exists.", Toast.LENGTH_SHORT).show()
                    } else {
                        addCustomItem(newItemName)
                    }
                } else {
                    Toast.makeText(this, "Item name cannot be empty.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    /**
     * Adds a new custom item to the list and updates Firestore.
     * @param itemName The name of the item to add.
     */
    private fun addCustomItem(itemName: String) {
        customItemsList.add(itemName) // Add to local list
        updateCustomItemsInFirestore() // Update Firestore and trigger adapter update
    }

    /**
     * Confirms deletion of an item and then deletes it.
     * @param itemToDelete The name of the item to delete.
     */
    private fun confirmDeleteItem(itemToDelete: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete '$itemToDelete'? This will also remove its data from past entries.")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteCustomItem(itemToDelete)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    /**
     * Deletes a custom item from the list and updates Firestore.
     * @param itemName The name of the item to delete.
     */
    private fun deleteCustomItem(itemName: String) {
        if (customItemsList.remove(itemName)) { // Remove from local list
            updateCustomItemsInFirestore() // Update Firestore and trigger adapter update
        } else {
            Toast.makeText(this, "Item not found in list.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Updates the 'custom_items' array in Firestore for the current profile.
     * Uses the current state of customItemsList field.
     */
    private fun updateCustomItemsInFirestore() {
        val profileDocRef = db.collection("artifacts")
            .document(getString(R.string.app_id))
            .collection("users")
            .document(userId)
            .collection("profiles")
            .document(profileName)

        profileDocRef.update("custom_items", customItemsList) // Use the field directly
            .addOnSuccessListener {
                Log.d("MANAGE_ITEMS_ACTIVITY", "Custom items updated in Firestore: $customItemsList")
                // Notify adapter that the data set has changed.
                // This is crucial for RecyclerView to re-render.
                customItemAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Items updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.w("MANAGE_ITEMS_ACTIVITY", "Error updating custom items", e)
                Toast.makeText(this, "Failed to update items: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
