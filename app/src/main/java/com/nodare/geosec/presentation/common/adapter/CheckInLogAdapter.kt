package com.nodare.geosec.presentation.common.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nodare.geosec.R
import com.nodare.geosec.data.model.CheckInLog
import com.nodare.geosec.databinding.ItemCheckinLogBinding
import com.nodare.geosec.util.Constants
import java.text.SimpleDateFormat
import java.util.Locale

class CheckInLogAdapter : ListAdapter<CheckInLog, CheckInLogAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCheckinLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemCheckinLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(log: CheckInLog) {
            val ctx = binding.root.context

            binding.tvUserName.text = log.userName.ifBlank { log.userId }
            binding.tvRole.text = log.role

            val roleColor = when (log.role) {
                Constants.ROLE_CEO -> R.color.severity_critical
                Constants.ROLE_ADMIN -> R.color.primary
                Constants.ROLE_TECHNICIAN -> R.color.accent
                Constants.ROLE_CAR_DRIVER -> R.color.success
                else -> R.color.primary
            }
            binding.tvRole.backgroundTintList = ContextCompat.getColorStateList(ctx, roleColor)

            val checkInStr = log.checkInTime?.toDate()?.let { dateFormat.format(it) } ?: "—"
            val checkOutStr = log.checkOutTime?.toDate()?.let { dateFormat.format(it) }
            binding.tvCheckInTime.text = if (checkOutStr != null) {
                "$checkInStr → $checkOutStr"
            } else {
                checkInStr
            }

            if (log.isActive) {
                binding.tvActiveStatus.text = "Active"
                binding.tvActiveStatus.setTextColor(ContextCompat.getColor(ctx, R.color.success))
                binding.viewActiveBar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.success))
            } else {
                binding.tvActiveStatus.text = "Checked out"
                binding.tvActiveStatus.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                binding.viewActiveBar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CheckInLog>() {
        override fun areItemsTheSame(a: CheckInLog, b: CheckInLog) = a.id == b.id
        override fun areContentsTheSame(a: CheckInLog, b: CheckInLog) = a == b
    }
}
