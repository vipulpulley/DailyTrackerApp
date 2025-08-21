package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ManageItemsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userId: String
    private lateinit var profileName: String
    private lateinit var customItemsRecyclerView: RecyclerView
    private lateinit var addItemButton: Button
    private lateinit var customItemAdapter: CustomItemAdapter
    private val customItemsList = mutableListOf<String>() // List to hold custom items

    // Notification UI elements
    private lateinit var notificationToggle: Switch
    private lateinit var notificationTimeTextView: TextView

    // Notification preferences
    private var notificationEnabled: Boolean = false
    private var notificationHour: Int = 20 // Default 8 PM
    private var notificationMinute: Int = 0 // Default 00 minutes

    private val NOTIFICATION_CHANNEL_ID = "daily_zindagi_reminder"
    private val NOTIFICATION_CHANNEL_NAME = "Daily Zindagi Reminder"

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
        notificationToggle = findViewById(R.id.notificationToggle)
        notificationTimeTextView = findViewById(R.id.notificationTimeTextView)

        // --- Set custom colors for the Switch (toggle button) ---
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked), // checked state
            intArrayOf(-android.R.attr.state_checked) // unchecked state
        )
        val thumbColors = intArrayOf(
            ContextCompat.getColor(this, R.color.green_button), // thumb color when ON
            ContextCompat.getColor(this, R.color.red_button) // thumb color when OFF
        )
        val trackColors = intArrayOf(
            ContextCompat.getColor(this, R.color.green_button), // track color when ON
            ContextCompat.getColor(this, R.color.red_button) // track color when OFF
        )

        notificationToggle.thumbTintList = ColorStateList(states, thumbColors)
        notificationToggle.trackTintList = ColorStateList(states, trackColors)
        // --- End custom colors for Switch ---


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

        // Load custom items and notification settings for this profile
        loadProfileSettings()

        // Set up Add Item button
        addItemButton.setOnClickListener {
            showAddItemDialog()
        }

        // Set up notification toggle listener
        notificationToggle.setOnCheckedChangeListener { _, isChecked ->
            notificationEnabled = isChecked
            updateNotificationSettingsInFirestore(true) // Show toast on user action
            if (isChecked) {
                requestNotificationPermission() // Request permission when enabling
            }
            // The cancel reminder toast is now handled within updateNotificationSettingsInFirestore(true)
        }

        // Set up notification time picker listener
        notificationTimeTextView.setOnClickListener {
            showTimePickerDialog()
        }

        // Create notification channel (safe to call multiple times)
        createNotificationChannel()
    }

    /**
     * Creates the notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = NOTIFICATION_CHANNEL_NAME
            val descriptionText = "Daily reminder to log entries for $profileName"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("NOTIFICATION", "Notification channel created: $NOTIFICATION_CHANNEL_ID")
        }
    }

    /**
     * Requests POST_NOTIFICATIONS permission for Android 13+
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
                // If permission granted, schedule the reminder
                NotificationScheduler.scheduleReminder(this, userId, profileName, notificationHour, notificationMinute, true) // Pass true for toast
            } else {
                Toast.makeText(this, "Notification permission denied. Reminders may not work.", Toast.LENGTH_LONG).show()
                notificationToggle.isChecked = false // Turn off toggle if permission denied
                notificationEnabled = false
                updateNotificationSettingsInFirestore(true) // Show toast on permission denial
            }
        }
    }

    /**
     * Shows a TimePickerDialog to allow the user to select the notification time.
     */
    private fun showTimePickerDialog() {
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
            notificationHour = hourOfDay
            notificationMinute = minute
            updateNotificationTimeDisplay()
            updateNotificationSettingsInFirestore(true) // Show toast on user action
            // The schedule reminder toast is now handled within updateNotificationSettingsInFirestore(true)
        }
        val timePickerDialog = TimePickerDialog(this, timeSetListener, notificationHour, notificationMinute, false) // false for 12-hour format
        timePickerDialog.show()
    }

    /**
     * Updates the TextView with the selected notification time.
     */
    private fun updateNotificationTimeDisplay() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, notificationHour)
            set(Calendar.MINUTE, notificationMinute)
        }
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        notificationTimeTextView.text = "Reminder Time: ${timeFormat.format(calendar.time)} (Tap to change)"
    }

    /**
     * Loads custom items and notification settings from Firestore for the current profile.
     */
    private fun loadProfileSettings() {
        val profileDocRef = db.collection("artifacts")
            .document(getString(R.string.app_id))
            .collection("users")
            .document(userId)
            .collection("profiles")
            .document(profileName)

        Log.d("MANAGE_ITEMS_ACTIVITY", "Loading profile settings from path: ${profileDocRef.path}")

        profileDocRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val items = document.get("custom_items") as? List<String>
                    if (items != null && items.isNotEmpty()) {
                        customItemsList.clear()
                        customItemsList.addAll(items)
                        customItemAdapter.notifyDataSetChanged()
                        Log.d("MANAGE_ITEMS_ACTIVITY", "Custom items loaded: $customItemsList")
                    } else {
                        Log.d("MANAGE_ITEMS_ACTIVITY", "No 'custom_items' field or it's empty. Initializing default items.")
                        initializeDefaultItems(false) // Do NOT show toast on initial default setup
                    }

                    // Load notification settings
                    notificationEnabled = document.getBoolean("notification_enabled") ?: false
                    val timeString = document.getString("notification_time")
                    if (!timeString.isNullOrEmpty()) {
                        try {
                            val parts = timeString.split(":")
                            notificationHour = parts[0].toInt()
                            notificationMinute = parts[1].toInt()
                        } catch (e: Exception) {
                            Log.e("NOTIFICATION", "Error parsing notification time: $timeString", e)
                            // Revert to default if parsing fails
                            notificationHour = 20
                            notificationMinute = 0
                        }
                    }
                    notificationToggle.isChecked = notificationEnabled
                    updateNotificationTimeDisplay()

                    // Schedule/cancel reminder based on loaded state (without toast)
                    if (notificationEnabled) {
                        NotificationScheduler.scheduleReminder(this, userId, profileName, notificationHour, notificationMinute, false) // Pass false for toast
                    } else {
                        NotificationScheduler.cancelReminder(this, userId, profileName)
                    }

                } else {
                    Log.d("MANAGE_ITEMS_ACTIVITY", "Profile document does not exist. Initializing default items and settings.")
                    initializeDefaultItems(false) // Do NOT show toast on initial default setup
                    // Set default notification settings for new profile
                    notificationEnabled = false // Default to off
                    notificationHour = 20
                    notificationMinute = 0
                    notificationToggle.isChecked = notificationEnabled
                    updateNotificationTimeDisplay()
                    updateNotificationSettingsInFirestore(false) // Do NOT show toast on initial default setup
                }
            }
            .addOnFailureListener { e ->
                Log.w("MANAGE_ITEMS_ACTIVITY", "Error loading profile settings", e)
                Toast.makeText(this, "Error loading settings: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Initializes a profile with default items if no custom items are found.
     * @param showToast Controls whether a toast message is displayed after updating Firestore.
     */
    private fun initializeDefaultItems(showToast: Boolean) {
        val defaultItems = listOf("Workout", "Medicines", "Happy")
        customItemsList.clear()
        customItemsList.addAll(defaultItems)
        updateCustomItemsInFirestore(showToast) // Pass showToast
    }

    /**
     * Updates the 'custom_items' array in Firestore for the current profile.
     * Uses the current state of customItemsList field.
     * @param showToast Controls whether a toast message is displayed after updating Firestore.
     */
    private fun updateCustomItemsInFirestore(showToast: Boolean) {
        val profileDocRef = db.collection("artifacts")
            .document(getString(R.string.app_id))
            .collection("users")
            .document(userId)
            .collection("profiles")
            .document(profileName)

        profileDocRef.update("custom_items", customItemsList)
            .addOnSuccessListener {
                Log.d("MANAGE_ITEMS_ACTIVITY", "Custom items updated in Firestore: $customItemsList")
                customItemAdapter.notifyDataSetChanged()
                if (showToast) { // Only show toast if explicitly requested
                    Toast.makeText(this, "Items updated successfully!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.w("MANAGE_ITEMS_ACTIVITY", "Error updating custom items", e)
                Toast.makeText(this, "Failed to update items: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Updates the notification settings (enabled, time) in Firestore for the current profile.
     * @param showToast Controls whether a toast message is displayed after updating Firestore.
     */
    private fun updateNotificationSettingsInFirestore(showToast: Boolean) {
        val profileDocRef = db.collection("artifacts")
            .document(getString(R.string.app_id))
            .collection("users")
            .document(userId)
            .collection("profiles")
            .document(profileName)

        val timeString = String.format(Locale.getDefault(), "%02d:%02d", notificationHour, notificationMinute)
        val updates = hashMapOf(
            "notification_enabled" to notificationEnabled,
            "notification_time" to timeString
        )

        profileDocRef.update(updates as Map<String, Any>) // Cast to Map<String, Any>
            .addOnSuccessListener {
                Log.d("NOTIFICATION", "Notification settings updated in Firestore: Enabled=$notificationEnabled, Time=$timeString")
                if (showToast) { // Only show toast if explicitly requested
                    if (notificationEnabled) {
                        NotificationScheduler.scheduleReminder(this, userId, profileName, notificationHour, notificationMinute, true) // Pass true for toast
                    } else {
                        // FIX: Explicitly show cancel toast when showToast is true and notification is disabled
                        NotificationScheduler.cancelReminder(this, userId, profileName)
                        Toast.makeText(this, "Daily reminder cancelled for $profileName.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // If not showing toast, just schedule/cancel based on state
                    if (notificationEnabled) {
                        NotificationScheduler.scheduleReminder(this, userId, profileName, notificationHour, notificationMinute, false) // Pass false for toast
                    } else {
                        NotificationScheduler.cancelReminder(this, userId, profileName)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w("NOTIFICATION", "Error updating notification settings", e)
                Toast.makeText(this, "Failed to save notification settings: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
        customItemsList.add(itemName)
        updateCustomItemsInFirestore(true) // Show toast on user action
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
        if (customItemsList.remove(itemName)) {
            updateCustomItemsInFirestore(true) // Show toast on user action
        } else {
            Toast.makeText(this, "Item not found in list.", Toast.LENGTH_SHORT).show()
        }
    }
}
