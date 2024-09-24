package com.example.plantpal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.plantpal.databinding.ItemPermissionRequestBinding

class PermissionRequestsAdapter(
    private val onAccept: (String) -> Unit,
    private val onReject: (String) -> Unit
) : ListAdapter<PermissionRequest, PermissionRequestsAdapter.ViewHolder>(PermissionRequestDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPermissionRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPermissionRequestBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(request: PermissionRequest) {
            binding.userIdTextView.text = request.userId
            binding.requestedRoleTextView.text = "Requested role: ${request.requestedRole}"

            binding.acceptButton.setOnClickListener {
                onAccept(request.id)
            }

            binding.rejectButton.setOnClickListener {
                onReject(request.id)
            }
        }
    }

    class PermissionRequestDiffCallback : DiffUtil.ItemCallback<PermissionRequest>() {
        override fun areItemsTheSame(oldItem: PermissionRequest, newItem: PermissionRequest): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PermissionRequest, newItem: PermissionRequest): Boolean {
            return oldItem == newItem
        }
    }
}