package com.nodare.geosec.presentation.common.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nodare.geosec.R
import com.nodare.geosec.data.model.SecurityAlert
import com.nodare.geosec.databinding.ItemAlertBinding
import java.text.SimpleDateFormat
import java.util.Locale

class AlertsAdapter(
    private val onResolveClick: (SecurityAlert) -> Unit
) : ListAdapter<SecurityAlert, AlertsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlertBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemAlertBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

        fun bind(alert: SecurityAlert) {
            binding.tvAlertType.text = alert.alertType.replace("_", " ").uppercase()
            binding.tvDescription.text = alert.description
            binding.tvUser.text = "User: ${alert.userName}"
            binding.tvSeverity.text = alert.severity.uppercase()
            binding.tvTimestamp.text = alert.timestamp?.toDate()?.let { dateFormat.format(it) } ?: ""

            if (alert.isResolved) {
                // Green color for resolved alerts
                val greenColor = ContextCompat.getColor(binding.root.context, R.color.success)
                binding.severityBar.setBackgroundColor(greenColor)
                binding.tvAlertType.setTextColor(greenColor)
                binding.tvSeverity.setTextColor(greenColor)
                binding.btnResolve.visibility = View.GONE
                binding.tvResolved.visibility = View.VISIBLE
            } else {
                // Original severity colors for unresolved alerts
                val severityColor = when (alert.severity) {
                    "critical" -> R.color.severity_critical
                    "high" -> R.color.severity_high
                    "medium" -> R.color.severity_medium
                    "low" -> R.color.severity_low
                    else -> R.color.text_secondary
                }
                val color = ContextCompat.getColor(binding.root.context, severityColor)
                binding.severityBar.setBackgroundColor(color)
                binding.tvAlertType.setTextColor(color)
                binding.tvSeverity.setTextColor(color)
                binding.btnResolve.visibility = View.VISIBLE
                binding.tvResolved.visibility = View.GONE
                binding.btnResolve.setOnClickListener { onResolveClick(alert) }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SecurityAlert>() {
        override fun areItemsTheSame(a: SecurityAlert, b: SecurityAlert) = a.id == b.id
        override fun areContentsTheSame(a: SecurityAlert, b: SecurityAlert) = a == b
    }
}
