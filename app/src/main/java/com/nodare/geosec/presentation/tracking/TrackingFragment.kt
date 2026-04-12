package com.nodare.geosec.presentation.tracking

import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.nodare.geosec.R
import com.nodare.geosec.databinding.FragmentTrackingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TrackingFragment : Fragment() {

    private var _binding: FragmentTrackingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TrackingViewModel by viewModels()

    private var googleMap: GoogleMap? = null
    private lateinit var employeeAdapter: TrackingEmployeeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDynamicMapHeight()
        setupEmployeeList()
        setupMap()
        setupRefreshButton()
        observeData()
        viewModel.observeActiveEmployees()
    }

    private fun setupDynamicMapHeight() {
        // Calculate map height as 55% of screen height for responsive sizing
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenHeight = displayMetrics.heightPixels
        val mapHeight = (screenHeight * 0.55).toInt()
        
        binding.mapContainer.layoutParams.height = mapHeight
        binding.mapContainer.requestLayout()
    }

    private fun setupEmployeeList() {
        employeeAdapter = TrackingEmployeeAdapter { employee ->
            if (employee != null) {
                // Focus map on selected employee
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(employee.latitude, employee.longitude), 16f
                    )
                )
            } else {
                // Deselected — zoom back to show all markers
                fitMapToAllMarkers()
            }
        }
        binding.recyclerEmployees.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerEmployees.adapter = employeeAdapter
        binding.recyclerEmployees.isNestedScrollingEnabled = true

        // Cap the RecyclerView max height to ~30% of screen so it stays fixed with scrollbar
        val displayMetrics = resources.displayMetrics
        val maxHeight = (displayMetrics.heightPixels * 0.30).toInt()
        binding.recyclerEmployees.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.height > maxHeight) {
                v.layoutParams = v.layoutParams.apply { height = maxHeight }
            }
        }
    }

    private fun setupMap() {
        binding.layoutMapLoading.visibility = View.VISIBLE
        binding.layoutNoData.visibility = View.GONE

        val mapFragment = childFragmentManager.findFragmentById(binding.mapFragment.id)
            as? com.google.android.gms.maps.SupportMapFragment

        mapFragment?.getMapAsync { map ->
            googleMap = map
            binding.layoutMapLoading.visibility = View.GONE

            // Enable all gestures for smooth map interaction
            map.uiSettings.apply {
                isZoomControlsEnabled = true
                isMapToolbarEnabled = false
                isScrollGesturesEnabled = true
                isZoomGesturesEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
                isScrollGesturesEnabledDuringRotateOrZoom = true
            }

            // Re-plot markers if data is already available
            viewModel.employeeLocations.value?.let { employees ->
                updateMapMarkers(employees)
            }
        } ?: run {
            binding.layoutMapLoading.visibility = View.GONE
            binding.layoutNoData.visibility = View.VISIBLE
        }
    }

    private fun setupRefreshButton() {
        binding.fabRefresh.setOnClickListener {
            viewModel.refresh()
        }
    }

    private fun observeData() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                binding.layoutMapLoading.visibility = View.VISIBLE
            }
        }

        viewModel.employeeLocations.observe(viewLifecycleOwner) { employees ->
            binding.layoutMapLoading.visibility = View.GONE

            if (employees.isEmpty()) {
                binding.layoutNoData.visibility = View.VISIBLE
                binding.tvNoEmployees.visibility = View.VISIBLE
                binding.recyclerEmployees.visibility = View.GONE
                binding.tvActiveCount.text = "0 active"
                googleMap?.clear()
            } else {
                binding.layoutNoData.visibility = View.GONE
                binding.tvNoEmployees.visibility = View.GONE
                binding.recyclerEmployees.visibility = View.VISIBLE
                binding.tvActiveCount.text = "${employees.size} active"

                employeeAdapter.submitList(employees)
                updateMapMarkers(employees)
            }
        }
    }

    private fun updateMapMarkers(employees: List<EmployeeTrackingInfo>) {
        val map = googleMap ?: return
        map.clear()

        // Only plot markers for employees with valid coordinates
        val locatedEmployees = employees.filter { it.latitude != 0.0 || it.longitude != 0.0 }
        if (locatedEmployees.isEmpty()) return

        val boundsBuilder = LatLngBounds.Builder()

        for (employee in locatedEmployees) {
            val position = LatLng(employee.latitude, employee.longitude)
            boundsBuilder.include(position)

            val markerColor = if (employee.isSuspicious) {
                BitmapDescriptorFactory.HUE_RED
            } else {
                BitmapDescriptorFactory.HUE_AZURE
            }

            val snippet = buildString {
                if (employee.speed > 0) append("Speed: %.1f km/h".format(employee.speed * 3.6))
                if (employee.isSuspicious) {
                    if (isNotEmpty()) append(" | ")
                    append("⚠ SUSPICIOUS")
                }
            }

            map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(employee.userName.ifBlank { "Employee" })
                    .snippet(snippet.ifBlank { "Active dispatch" })
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
            )
        }

        fitMapToAllMarkers()
    }

    private fun fitMapToAllMarkers() {
        val map = googleMap ?: return
        val employees = viewModel.employeeLocations.value ?: return
        val locatedEmployees = employees.filter { it.latitude != 0.0 || it.longitude != 0.0 }
        if (locatedEmployees.isEmpty()) return

        if (locatedEmployees.size == 1) {
            val first = locatedEmployees.first()
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(first.latitude, first.longitude), 14f
                )
            )
            return
        }

        try {
            val boundsBuilder = LatLngBounds.Builder()
            for (emp in locatedEmployees) {
                boundsBuilder.include(LatLng(emp.latitude, emp.longitude))
            }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
        } catch (_: Exception) {
            val first = locatedEmployees.first()
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(first.latitude, first.longitude), 14f
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
        _binding = null
    }
}
