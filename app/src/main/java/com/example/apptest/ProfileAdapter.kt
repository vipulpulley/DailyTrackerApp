package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button // Import Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProfileAdapter(
    private val profiles: List<String>,
    private val onItemClick: (String) -> Unit, // For selecting profile
    private val onManageItemsClick: (String) -> Unit // New: For managing items
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileNameTextView: TextView = itemView.findViewById(R.id.profileNameTextView)
        val manageItemsButton: Button = itemView.findViewById(R.id.manageItemsButton) // Reference the new button
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profileName = profiles[position]
        holder.profileNameTextView.text = profileName
        holder.itemView.setOnClickListener {
            onItemClick(profileName) // Still handles clicking the profile name
        }
        holder.manageItemsButton.setOnClickListener {
            onManageItemsClick(profileName) // Handle clicking the manage items button
        }
    }

    override fun getItemCount(): Int = profiles.size
}