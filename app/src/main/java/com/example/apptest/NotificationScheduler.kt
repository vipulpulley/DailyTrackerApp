package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar
import android.content.pm.PackageManager // ADDED: Import for PackageManager

object NotificationScheduler {

    private const val NOTIFICATION_REQUEST_CODE_BASE = 1000 // Base for unique request codes

    /**
     * Schedules a daily reminder notification for a specific profile.
     * @param context The application context.
     * @param userId The ID of the authenticated user.
     * @param profileName The name of the profile for which to schedule the reminder.
     * @param hourOfDay The hour (0-23) for the reminder.
     * @param minute The minute (0-59) for the reminder.
     */
    fun scheduleReminder(context: Context, userId: String, profileName: String, hourOfDay: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = "com.example.apptest.DAILY_REMINDER" // Custom action for our alarm
            putExtra("USER_ID", userId)
            putExtra("PROFILE_NAME", profileName)
        }

        // Create a unique request code for each profile's notification
        // Using a hash of the profile name for uniqueness
        val requestCode = NOTIFICATION_REQUEST_CODE_BASE + profileName.hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // FLAG_IMMUTABLE required for Android 6.0+
        )

        // Set the alarm time
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If the set time is in the past, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Use setExactAndAllowWhileIdle for exact alarms (for Android M+)
        // Use setAlarmClock for Android N+ for better reliability (shows next alarm in status bar)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+ requires SCHEDULE_EXACT_ALARM permission
                // Check if SCHEDULE_EXACT_ALARM permission is granted
                if (context.checkSelfPermission(android.Manifest.permission.SCHEDULE_EXACT_ALARM) == PackageManager.PERMISSION_GRANTED) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, null) // No show intent
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    Log.d("NOTIFICATION_SCHEDULER", "Exact alarm (AlarmClock) scheduled for $profileName at ${calendar.time} (ReqCode: $requestCode)")
                } else {
                    // Fallback if permission not granted
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    Log.w("NOTIFICATION_SCHEDULER", "SCHEDULE_EXACT_ALARM permission not granted. Falling back to setExactAndAllowWhileIdle for $profileName at ${calendar.time} (ReqCode: $requestCode)")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                Log.d("NOTIFICATION_SCHEDULER", "Exact alarm (setExactAndAllowWhileIdle) scheduled for $profileName at ${calendar.time} (ReqCode: $requestCode)")
            }
        } else {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pendingIntent)
            Log.d("NOTIFICATION_SCHEDULER", "Repeating alarm scheduled for $profileName at ${calendar.time} (ReqCode: $requestCode)")
        }
    }

    /**
     * Cancels a previously scheduled reminder notification for a specific profile.
     * @param context The application context.
     * @param userId The ID of the authenticated user.
     * @param profileName The name of the profile for which to cancel the reminder.
     */
    fun cancelReminder(context: Context, userId: String, profileName: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = "com.example.apptest.DAILY_REMINDER" // Must match the action used for scheduling
            putExtra("USER_ID", userId) // Ensure extras match for PendingIntent equality
            putExtra("PROFILE_NAME", profileName)
        }

        val requestCode = NOTIFICATION_REQUEST_CODE_BASE + profileName.hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE // FLAG_NO_CREATE means don't create if it doesn't exist
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            Log.d("NOTIFICATION_SCHEDULER", "Reminder cancelled for $profileName (ReqCode: $requestCode)")
        } else {
            Log.d("NOTIFICATION_SCHEDULER", "No pending intent found to cancel for $profileName (ReqCode: $requestCode)")
        }
    }
}
