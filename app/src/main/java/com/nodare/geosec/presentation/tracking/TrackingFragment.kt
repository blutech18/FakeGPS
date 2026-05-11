package com.nodare.geosec.presentation.tracking

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
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

    // Live markers keyed by userId — reused for smooth animation instead of clearing the map
    private val liveMarkers = mutableMapOf<String, Marker>()

    // Running animations keyed by userId so we can cancel them on new updates
    private val markerAnimators = mutableMapOf<String, ValueAnimator>()

    // Cached BitmapDescriptors for the custom GPS arrow icons
    private var normalArrowIcon: BitmapDescriptor? = null
    private var suspiciousArrowIcon: BitmapDescriptor? = null

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
        prepareMarkerIcons()
        setupDynamicMapHeight()
        setupEmployeeList()
        setupMap()
        setupRefreshButton()
        observeData()
        viewModel.observeActiveEmployees()
    }

    // ── Icon preparation ────────────────────────────────────────────────

    /**
     * Convert vector drawables to BitmapDescriptors once, so we don't
     * re-render them on every marker update.
     */
    private fun prepareMarkerIcons() {
        normalArrowIcon = vectorToBitmapDescriptor(R.drawable.ic_gps_arrow, 64)
        suspiciousArrowIcon = vectorToBitmapDescriptor(R.drawable.ic_gps_arrow_suspicious, 64)
    }

    private fun vectorToBitmapDescriptor(drawableRes: Int, sizeDp: Int): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        val drawable = ContextCompat.getDrawable(requireContext(), drawableRes)!!
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // ── Layout setup ────────────────────────────────────────────────────

    private fun setupDynamicMapHeight() {
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
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(employee.latitude, employee.longitude), 16f
                    )
                )
            } else {
                fitMapToAllMarkers()
            }
        }
        binding.recyclerEmployees.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerEmployees.adapter = employeeAdapter
        binding.recyclerEmployees.isNestedScrollingEnabled = true

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
            // Clear all existing markers and animations on manual refresh
            clearAllMarkers()
            viewModel.refresh()
        }
    }

    // ── Data observation ────────────────────────────────────────────────

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
                clearAllMarkers()
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

    // ── Real-time marker management ─────────────────────────────────────

    /**
     * Updates map markers with smooth animation.
     * Instead of clearing and re-adding all markers (which causes flicker),
     * we reuse existing markers and animate them to new positions — like
     * Waze/Grab showing a rider moving in real-time.
     */
    private fun updateMapMarkers(employees: List<EmployeeTrackingInfo>) {
        val map = googleMap ?: return

        val locatedEmployees = employees.filter { it.latitude != 0.0 || it.longitude != 0.0 }
        val activeUserIds = employees.map { it.userId }.toSet()

        // Remove markers for employees no longer in the list
        val removedIds = liveMarkers.keys - activeUserIds
        for (id in removedIds) {
            markerAnimators[id]?.cancel()
            markerAnimators.remove(id)
            liveMarkers[id]?.remove()
            liveMarkers.remove(id)
        }

        for (employee in locatedEmployees) {
            val newPosition = LatLng(employee.latitude, employee.longitude)
            val icon = if (employee.isSuspicious) suspiciousArrowIcon else normalArrowIcon

            val existingMarker = liveMarkers[employee.userId]

            if (existingMarker != null) {
                // Animate existing marker to new position (smooth movement)
                animateMarkerTo(employee.userId, existingMarker, newPosition)

                // Update rotation (bearing) for directional arrow
                existingMarker.rotation = employee.bearing

                // Update icon if suspicious status changed
                existingMarker.setIcon(icon)

                // Update info window content
                existingMarker.title = employee.userName.ifBlank { "Employee" }
                existingMarker.snippet = buildSnippet(employee)
            } else {
                // Create new marker with custom GPS arrow icon
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(newPosition)
                        .title(employee.userName.ifBlank { "Employee" })
                        .snippet(buildSnippet(employee))
                        .icon(icon)
                        .rotation(employee.bearing)
                        .anchor(0.5f, 0.5f) // Center the arrow on the position
                        .flat(true) // Flat marker rotates with the map
                )
                if (marker != null) {
                    liveMarkers[employee.userId] = marker
                }
            }
        }

        // Also handle employees with no location (0,0) — remove their markers if they exist
        for (employee in employees) {
            if (employee.latitude == 0.0 && employee.longitude == 0.0) {
                liveMarkers[employee.userId]?.remove()
                liveMarkers.remove(employee.userId)
                markerAnimators[employee.userId]?.cancel()
                markerAnimators.remove(employee.userId)
            }
        }

        // Only auto-fit bounds if no employee is selected (user might be focused on one)
        if (employeeAdapter.getSelectedUserId() == null && locatedEmployees.isNotEmpty()) {
            fitMapToAllMarkers()
        }
    }

    /**
     * Smoothly animate a marker from its current position to a new position.
     * This creates the "moving rider" effect like Waze/Grab.
     */
    private fun animateMarkerTo(userId: String, marker: Marker, toPosition: LatLng) {
        // Cancel any running animation for this marker
        markerAnimators[userId]?.cancel()

        val startPosition = marker.position
        // Skip animation if positions are the same
        if (startPosition.latitude == toPosition.latitude &&
            startPosition.longitude == toPosition.longitude
        ) return

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500 // 1.5 seconds for smooth movement
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                val lat = startPosition.latitude +
                        (toPosition.latitude - startPosition.latitude) * fraction
                val lng = startPosition.longitude +
                        (toPosition.longitude - startPosition.longitude) * fraction
                marker.position = LatLng(lat, lng)
            }
        }

        markerAnimators[userId] = animator
        animator.start()
    }

    private fun buildSnippet(employee: EmployeeTrackingInfo): String {
        return buildString {
            if (employee.speed > 0) {
                append("Speed: %.1f km/h".format(employee.speed * 3.6))
            }
            if (employee.isSuspicious) {
                if (isNotEmpty()) append(" | ")
                append("⚠ SUSPICIOUS")
            }
            if (isEmpty()) append("Active dispatch")
        }
    }

    // ── Camera helpers ──────────────────────────────────────────────────

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

    // ── Cleanup ─────────────────────────────────────────────────────────

    private fun clearAllMarkers() {
        markerAnimators.values.forEach { it.cancel() }
        markerAnimators.clear()
        liveMarkers.values.forEach { it.remove() }
        liveMarkers.clear()
        googleMap?.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearAllMarkers()
        googleMap = null
        _binding = null
    }
}
