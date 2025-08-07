package com.example.apptest // IMPORTANT: Ensure this matches your package name

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CustomItemAdapter(
    private val items: MutableList<String>, // Mutable list to allow adding/removing
    private val onDeleteClick: (String) -> Unit // Callback for delete button
) : RecyclerView.Adapter<CustomItemAdapter.CustomItemViewHolder>() {

    class CustomItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemNameTextView: TextView = itemView.findViewById(R.id.itemNameTextView)
        val deleteItemButton: ImageButton = itemView.findViewById(R.id.deleteItemButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_custom_item, parent, false)
        return CustomItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: CustomItemViewHolder, position: Int) {
        val itemName = items[position]
        holder.itemNameTextView.text = itemName
        holder.deleteItemButton.setOnClickListener {
            onDeleteClick(itemName)
        }
    }

    override fun getItemCount(): Int = items.size

    // Helper method to update the list and notify adapter
    fun updateItems(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
