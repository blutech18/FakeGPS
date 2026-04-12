package com.nodare.geosec.presentation.alerts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.nodare.geosec.R
import com.nodare.geosec.databinding.FragmentAlertsBinding
import com.nodare.geosec.presentation.common.adapter.AlertsAdapter
import com.nodare.geosec.util.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AlertsViewModel by viewModels()
    private lateinit var adapter: AlertsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilterChips()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = AlertsAdapter { alert ->
            if (!alert.isResolved) {
                viewModel.resolveAlert(alert.id)
            }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedChangeListener { _, checkedId ->
            val filter = when (checkedId) {
                R.id.chipUnresolved -> AlertFilter.UNRESOLVED
                R.id.chipResolved -> AlertFilter.RESOLVED
                else -> AlertFilter.ALL
            }
            viewModel.setFilter(filter)
        }
    }

    private fun observeData() {
        viewModel.alerts.observe(viewLifecycleOwner) { alerts ->
            adapter.submitList(alerts)
            binding.tvEmpty.visibility = if (alerts.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.resolveState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Success -> Toast.makeText(requireContext(), "Alert resolved", Toast.LENGTH_SHORT).show()
                is Resource.Error -> Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
