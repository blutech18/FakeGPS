package com.nodare.geosec.presentation.common.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nodare.geosec.R
import com.nodare.geosec.data.model.DispatchSession
import com.nodare.geosec.databinding.ItemDispatchHistoryBinding
import java.text.SimpleDateFormat
import java.util.Locale

class DispatchHistoryAdapter : ListAdapter<DispatchSession, DispatchHistoryAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDispatchHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemDispatchHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: DispatchSession) {
            // Show formatted dispatch reference instead of raw Firestore ID
            val displayRef = if (session.dispatchKey.isNotBlank()) {
                session.dispatchKey.take(8).uppercase()
            } else {
                session.id.take(6).uppercase()
            }
            val nameLabel = if (session.userName.isNotBlank()) "${session.userName} · " else ""
            binding.tvSessionId.text = "${nameLabel}Dispatch #$displayRef"

            val statusText = when (session.status) {
                "active" -> "In Progress"
                "completed" -> "Completed"
                "suspicious" -> "Flagged"
                else -> session.status.replaceFirstChar { it.uppercase() }
            }
            binding.tvStatus.text = statusText

            val (statusColor, barColor) = when (session.status) {
                "active" -> R.color.success to R.color.success
                "completed" -> R.color.primary to R.color.primary
                "suspicious" -> R.color.error to R.color.error
                else -> R.color.text_secondary to R.color.text_secondary
            }

            val ctx = binding.root.context
            binding.tvStatus.backgroundTintList = ContextCompat.getColorStateList(ctx, statusColor)
            binding.viewStatusBar.setBackgroundColor(ContextCompat.getColor(ctx, barColor))

            val startTimeStr = session.startTime?.toDate()?.let { dateFormat.format(it) } ?: "—"
            val endTimeStr = session.endTime?.toDate()?.let { dateFormat.format(it) }
            binding.tvDateTime.text = if (endTimeStr != null) {
                "$startTimeStr → $endTimeStr"
            } else {
                "$startTimeStr (ongoing)"
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DispatchSession>() {
        override fun areItemsTheSame(a: DispatchSession, b: DispatchSession) = a.id == b.id
        override fun areContentsTheSame(a: DispatchSession, b: DispatchSession) = a == b
    }
}
