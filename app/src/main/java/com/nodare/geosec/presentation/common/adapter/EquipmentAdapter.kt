package com.nodare.geosec.presentation.common.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nodare.geosec.R
import com.nodare.geosec.data.model.Equipment
import com.nodare.geosec.databinding.ItemEquipmentBinding
import com.nodare.geosec.util.Constants

class EquipmentAdapter(
    private val isAdmin: Boolean,
    private val onEditClick: (Equipment) -> Unit
) : ListAdapter<Equipment, EquipmentAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEquipmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemEquipmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(equipment: Equipment) {
            binding.tvEquipmentId.text = equipment.equipmentId
            binding.tvEquipmentName.text = equipment.equipmentName
            binding.tvCategory.text = equipment.category
            binding.tvStatus.text = equipment.status

            val statusColor = when (equipment.status) {
                Constants.STATUS_REPAIRED -> R.color.status_repaired
                Constants.STATUS_TO_BE_REPAIRED -> R.color.status_to_be_repaired
                Constants.STATUS_PULL_OUT -> R.color.status_pull_out
                else -> R.color.text_secondary
            }
            binding.tvStatus.setTextColor(ContextCompat.getColor(binding.root.context, statusColor))

            binding.btnEdit.visibility = if (isAdmin) View.VISIBLE else View.GONE
            binding.btnEdit.setOnClickListener { onEditClick(equipment) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Equipment>() {
        override fun areItemsTheSame(a: Equipment, b: Equipment) = a.id == b.id
        override fun areContentsTheSame(a: Equipment, b: Equipment) = a == b
    }
}
