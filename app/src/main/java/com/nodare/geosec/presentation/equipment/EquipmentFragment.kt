package com.nodare.geosec.presentation.equipment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nodare.geosec.R
import com.nodare.geosec.data.model.Equipment
import com.nodare.geosec.databinding.FragmentEquipmentBinding
import com.nodare.geosec.databinding.DialogEquipmentBinding
import com.nodare.geosec.presentation.common.adapter.EquipmentAdapter
import com.nodare.geosec.presentation.dashboard.MainViewModel
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EquipmentFragment : Fragment() {

    private var _binding: FragmentEquipmentBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EquipmentViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: EquipmentAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEquipmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Check if user is already loaded (navigating from another fragment)
        if (mainViewModel.isUserLoaded && !::adapter.isInitialized) {
            setupRecyclerView()
            setupFab()
            observeData()
        }

        // Wait for user data so isAdmin is accurate before setting up adapter
        mainViewModel.currentUser.observe(viewLifecycleOwner) { state ->
            if (state is Resource.Success && !::adapter.isInitialized) {
                setupRecyclerView()
                setupFab()
                observeData()
            }
        }
    }

    private fun setupRecyclerView() {
        // CEO/Admin can add and edit; Technician/Car Driver view only
        adapter = EquipmentAdapter(
            isAdmin = mainViewModel.isAdmin,
            onEditClick = { equipment -> showEditDialog(equipment) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupFab() {
        // CEO/Admin can add equipment; Technician/Car Driver cannot
        binding.fabAdd.visibility = if (mainViewModel.isAdmin) View.VISIBLE else View.GONE
        binding.fabAdd.setOnClickListener { showAddDialog() }
    }

    private fun observeData() {
        viewModel.equipment.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.operationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Success -> Toast.makeText(requireContext(), "Operation successful", Toast.LENGTH_SHORT).show()
                is Resource.Error -> Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogEquipmentBinding.inflate(layoutInflater)
        setupStatusSpinner(dialogBinding)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Equipment")
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val equipment = Equipment(
                    equipmentId = dialogBinding.etEquipmentId.text.toString().trim(),
                    equipmentName = dialogBinding.etEquipmentName.text.toString().trim(),
                    category = dialogBinding.etCategory.text.toString().trim(),
                    status = dialogBinding.spinnerStatus.selectedItem.toString()
                )
                viewModel.addEquipment(equipment)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(equipment: Equipment) {
        val dialogBinding = DialogEquipmentBinding.inflate(layoutInflater)
        setupStatusSpinner(dialogBinding)

        dialogBinding.etEquipmentId.setText(equipment.equipmentId)
        dialogBinding.etEquipmentId.isEnabled = false
        dialogBinding.etEquipmentName.setText(equipment.equipmentName)
        dialogBinding.etCategory.setText(equipment.category)

        val statuses = listOf(Constants.STATUS_REPAIRED, Constants.STATUS_TO_BE_REPAIRED, Constants.STATUS_PULL_OUT)
        val statusIndex = statuses.indexOf(equipment.status)
        if (statusIndex >= 0) dialogBinding.spinnerStatus.setSelection(statusIndex)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Equipment")
            .setView(dialogBinding.root)
            .setPositiveButton("Update") { _, _ ->
                viewModel.updateEquipment(
                    documentId = equipment.id,
                    name = dialogBinding.etEquipmentName.text.toString().trim(),
                    category = dialogBinding.etCategory.text.toString().trim(),
                    status = dialogBinding.spinnerStatus.selectedItem.toString()
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupStatusSpinner(dialogBinding: DialogEquipmentBinding) {
        val statuses = listOf(Constants.STATUS_REPAIRED, Constants.STATUS_TO_BE_REPAIRED, Constants.STATUS_PULL_OUT)
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, statuses)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerStatus.adapter = spinnerAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
