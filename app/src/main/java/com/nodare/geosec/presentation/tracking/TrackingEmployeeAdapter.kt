package com.nodare.geosec.presentation.tracking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nodare.geosec.R
import com.nodare.geosec.databinding.ItemTrackingEmployeeBinding

class TrackingEmployeeAdapter(
    private val onEmployeeClick: (EmployeeTrackingInfo?) -> Unit
) : ListAdapter<EmployeeTrackingInfo, TrackingEmployeeAdapter.ViewHolder>(DiffCallback()) {

    private var selectedUserId: String? = null

    fun setSelectedEmployee(userId: String?) {
        val oldSelected = selectedUserId
        // Toggle: if tapping the already-selected employee, deselect
        selectedUserId = if (oldSelected == userId) null else userId
        currentList.forEachIndexed { index, info ->
            if (info.userId == oldSelected || info.userId == userId) {
                notifyItemChanged(index)
            }
        }
    }

    fun getSelectedUserId(): String? = selectedUserId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrackingEmployeeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemTrackingEmployeeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(info: EmployeeTrackingInfo) {
            binding.tvEmployeeName.text = info.userName.ifBlank { "Unknown" }

            val hasLocation = info.latitude != 0.0 || info.longitude != 0.0
            val statusText = when {
                info.isSuspicious -> "Suspicious"
                !hasLocation -> "Awaiting GPS"
                else -> "Dispatching"
            }
            binding.tvEmployeeStatus.text = statusText

            val dotColor = when {
                info.isSuspicious -> ContextCompat.getColor(itemView.context, R.color.error)
                !hasLocation -> ContextCompat.getColor(itemView.context, R.color.warning)
                else -> ContextCompat.getColor(itemView.context, R.color.success)
            }
            binding.viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(dotColor)

            val isSelected = info.userId == selectedUserId
            val bgColor = if (isSelected) {
                ContextCompat.getColor(itemView.context, R.color.primary_lightest)
            } else {
                ContextCompat.getColor(itemView.context, R.color.surface_blue)
            }
            (itemView as? com.google.android.material.card.MaterialCardView)?.setCardBackgroundColor(bgColor)

            itemView.setOnClickListener {
                val wasSelected = info.userId == selectedUserId
                setSelectedEmployee(info.userId)
                // If toggled off, pass null to signal deselection
                onEmployeeClick(if (wasSelected) null else info)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<EmployeeTrackingInfo>() {
        override fun areItemsTheSame(a: EmployeeTrackingInfo, b: EmployeeTrackingInfo) =
            a.userId == b.userId

        override fun areContentsTheSame(a: EmployeeTrackingInfo, b: EmployeeTrackingInfo) =
            a == b
    }
}
