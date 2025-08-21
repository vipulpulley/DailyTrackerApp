package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.ContextCompat // ADDED: Import for ContextCompat

class DailyReminderReceiver : BroadcastReceiver() {

    private val NOTIFICATION_CHANNEL_ID = "daily_zindagi_reminder"
    private val NOTIFICATION_ID_BASE = 100 // Base for unique notification IDs

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("DAILY_REMINDER_RECEIVER", "onReceive triggered. Action: ${intent?.action}")

        // Re-initialize Firebase if needed (important for receivers triggered by AlarmManager/Boot)
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
            Log.d("DAILY_REMINDER_RECEIVER", "FirebaseApp initialized in Receiver.")
        }

        val auth = Firebase.auth
        val db = Firebase.firestore

        // Handle BOOT_COMPLETED action to reschedule alarms
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("DAILY_REMINDER_RECEIVER", "Device booted. Rescheduling all active reminders.")
            rescheduleAllRemindersOnBoot(context, auth, db)
            return // Exit after handling boot completed
        }

        // Handle our custom reminder action
        if (intent?.action == "com.example.apptest.DAILY_REMINDER") {
            val userId = intent.getStringExtra("USER_ID")
            val profileName = intent.getStringExtra("PROFILE_NAME")

            if (userId.isNullOrEmpty() || profileName.isNullOrEmpty()) {
                Log.e("DAILY_REMINDER_RECEIVER", "Missing userId or profileName in reminder intent.")
                return
            }

            Log.d("DAILY_REMINDER_RECEIVER", "Checking entry for User: $userId, Profile: $profileName")

            // Check if user is still authenticated (optional, but good practice)
            if (auth.currentUser == null || auth.currentUser?.uid != userId) {
                Log.w("DAILY_REMINDER_RECEIVER", "User not authenticated or UID mismatch. Skipping notification for $profileName.")
                // Optionally, sign in anonymously if you want to check data for anonymous users
                // For now, we assume the user is authenticated from the main app flow.
                return
            }

            // Check if an entry was made for today for this profile
            checkIfEntryMadeToday(context, db, userId, profileName)
        }
    }

    /**
     * Checks Firestore if an entry for the current date exists for the given profile.
     * If no entry is found, it displays a notification.
     */
    private fun checkIfEntryMadeToday(context: Context, db: FirebaseFirestore, userId: String, profileName: String) {
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val docRef = db.collection("artifacts")
            .document(context.getString(R.string.app_id))
            .collection("users")
            .document(userId)
            .collection("profiles")
            .document(profileName)
            .collection("daily_tracker")
            .document(todayDate)

        docRef.get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    Log.d("DAILY_REMINDER_RECEIVER", "No entry found for $profileName today. Displaying notification.")
                    showNotification(context, profileName)
                } else {
                    Log.d("DAILY_REMINDER_RECEIVER", "Entry found for $profileName today. Skipping notification.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("DAILY_REMINDER_RECEIVER", "Error checking daily entry for notification: ${e.message}", e)
                // Even on error, show notification to remind, or handle error gracefully
                showNotification(context, profileName) // Decide if you want to notify on error
            }
    }

    /**
     * Displays a notification to the user.
     */
    private fun showNotification(context: Context, profileName: String) {
        val notificationManager = NotificationManagerCompat.from(context)

        // Build the notification
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use your app icon
            .setContentTitle("Zindagi Daily Reminder")
            .setContentText("Don't forget to log your entries for '$profileName' today!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Dismisses the notification when tapped

        // Check for POST_NOTIFICATIONS permission before showing (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                // Use a unique ID for each profile's notification
                val notificationId = NOTIFICATION_ID_BASE + profileName.hashCode()
                notificationManager.notify(notificationId, builder.build())
                Log.d("DAILY_REMINDER_RECEIVER", "Notification displayed for $profileName (ID: $notificationId)")
            } else {
                Log.w("DAILY_REMINDER_RECEIVER", "POST_NOTIFICATIONS permission not granted. Cannot show notification.")
            }
        } else {
            // For older Android versions, no explicit permission check needed at runtime
            val notificationId = NOTIFICATION_ID_BASE + profileName.hashCode()
            notificationManager.notify(notificationId, builder.build())
            Log.d("DAILY_REMINDER_RECEIVER", "Notification displayed for $profileName (ID: $notificationId) (Older Android).")
        }
    }

    /**
     * Reschedules all active reminders on device boot.
     * This requires the RECEIVE_BOOT_COMPLETED permission and a corresponding intent-filter in AndroidManifest.
     */
    private fun rescheduleAllRemindersOnBoot(context: Context, auth: FirebaseAuth, db: FirebaseFirestore) {
        // This function needs to re-authenticate or ensure auth state is ready to query Firestore.
        // For simplicity, we'll assume anonymous auth is sufficient for fetching profile settings.
        // In a real app, you might need to handle re-authentication more robustly here.

        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = task.result?.user?.uid
                    if (userId != null) {
                        Log.d("DAILY_REMINDER_RECEIVER", "Rescheduling: Signed in anonymously with UID: $userId")
                        val profilesCollectionRef = db.collection("artifacts")
                            .document(context.getString(R.string.app_id))
                            .collection("users")
                            .document(userId)
                            .collection("profiles")

                        profilesCollectionRef.get()
                            .addOnSuccessListener { querySnapshot ->
                                for (document in querySnapshot.documents) {
                                    val profileName = document.id
                                    val notificationEnabled = document.getBoolean("notification_enabled") ?: false
                                    val timeString = document.getString("notification_time")

                                    if (notificationEnabled && !timeString.isNullOrEmpty()) {
                                        try {
                                            val parts = timeString.split(":")
                                            val hour = parts[0].toInt()
                                            val minute = parts[1].toInt()
                                            NotificationScheduler.scheduleReminder(context, userId, profileName, hour, minute)
                                            Log.d("DAILY_REMINDER_RECEIVER", "Rescheduled reminder for $profileName at $timeString.")
                                        } catch (e: Exception) {
                                            Log.e("DAILY_REMINDER_RECEIVER", "Error parsing time for rescheduling profile $profileName: $timeString", e)
                                        }
                                    }
                                }
                                Log.d("DAILY_REMINDER_RECEIVER", "Finished rescheduling active reminders.")
                            }
                            .addOnFailureListener { e ->
                                Log.e("DAILY_REMINDER_RECEIVER", "Error fetching profiles for rescheduling: ${e.message}", e)
                            }
                    } else {
                        Log.e("DAILY_REMINDER_RECEIVER", "Rescheduling: Anonymous sign-in failed or UID is null.")
                    }
                } else {
                    Log.e("DAILY_REMINDER_RECEIVER", "Rescheduling: Anonymous sign-in failed on boot. Cannot fetch profiles.", task.exception)
                }
            }
    }
}
