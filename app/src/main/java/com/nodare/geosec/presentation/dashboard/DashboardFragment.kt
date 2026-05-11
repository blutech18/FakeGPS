package com.nodare.geosec.presentation.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.location.LocationServices
import com.nodare.geosec.R
import com.nodare.geosec.data.repository.EmployeeStatus
import com.nodare.geosec.databinding.FragmentDashboardBinding
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.DeviceUtils
import com.nodare.geosec.util.Resource
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.nodare.geosec.presentation.dashboard.ActivityType

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()
    private val dashboardViewModel: DashboardViewModel by activityViewModels()
    private var roleConfigured = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            performCheckIn()
        } else {
            Toast.makeText(requireContext(), "Location permission is required for check-in", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Show loading state initially while waiting for user data
        binding.layoutLoading.visibility = View.VISIBLE
        
        observeUser()
    }

    private fun observeUser() {
        mainViewModel.currentUser.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Success -> {
                    binding.layoutLoading.visibility = View.GONE
                    binding.layoutContent.visibility = View.VISIBLE
                    val user = state.data
                    binding.tvWelcome.text = "Welcome, ${user.displayName}"

                    val isAdmin = user.role == Constants.ROLE_CEO || user.role == Constants.ROLE_ADMIN

                    // Admin-only cards
                    binding.cardAdminPanel.visibility = if (isAdmin) View.VISIBLE else View.GONE
                    binding.cardAdminStats.visibility = if (isAdmin) View.VISIBLE else View.GONE
                    binding.cardRecentActivity.visibility = if (isAdmin) View.VISIBLE else View.GONE
                    binding.cardEmployeeStatus.visibility = if (isAdmin) View.VISIBLE else View.GONE

                    // Non-admin: check-in/out buttons + status cards
                    binding.cardCheckInStatus.visibility = if (!isAdmin) View.VISIBLE else View.GONE
                    binding.layoutCheckInActions.visibility = if (!isAdmin) View.VISIBLE else View.GONE
                    binding.cardDispatchStatus.visibility = if (!isAdmin) View.VISIBLE else View.GONE

                    // Info card for non-admin roles only
                    binding.cardDispatchInfo.visibility = if (!isAdmin) View.VISIBLE else View.GONE

                    // New driver dashboard cards
                    binding.cardDriverSummary.visibility = if (!isAdmin) View.VISIBLE else View.GONE

                    // Always set up observers (they use viewLifecycleOwner which changes on navigation)
                    if (isAdmin) {
                        setupAdminUI()
                        observeAdminStats()
                        observeEmployeeStatuses()
                        dashboardViewModel.loadAdminStats()
                        dashboardViewModel.loadEmployeeStatuses()
                    } else {
                        if (!roleConfigured) {
                            setupCheckInButtons()
                        }
                        observeDriverStatus()
                        observeCheckInActions()
                        observeDriverSummary()
                        dashboardViewModel.loadDriverStatus(mainViewModel.userId)
                    }
                    roleConfigured = true

                    // Role-specific badge color and info card content
                    configureForRole(user.role)
                }
                is Resource.Error -> {
                    binding.layoutLoading.visibility = View.GONE
                    binding.layoutContent.visibility = View.VISIBLE
                    binding.tvWelcome.text = "Error loading profile"
                }
                is Resource.Loading -> {
                    binding.layoutLoading.visibility = View.VISIBLE
                    binding.tvWelcome.text = "Loading..."
                }
            }
        }
    }

    private fun configureForRole(role: String) {
        // Role-specific info card content
        when (role) {
            Constants.ROLE_CEO -> {
                binding.tvInfoTitle.text = "Executive Overview"
                binding.tvInfoDescription.text = "Oversee all dispatch operations, review security alerts, and monitor technician and driver activity."
            }
            Constants.ROLE_ADMIN -> {
                binding.tvInfoTitle.text = "Admin Dashboard"
                binding.tvInfoDescription.text = "Manage technicians and drivers, handle check-in logs, manage storage inventory, and resolve security alerts."
            }
            Constants.ROLE_TECHNICIAN -> {
                binding.tvInfoTitle.text = "Technician Dispatch"
                binding.tvInfoDescription.text = "Check in, start equipment installation or retrieval dispatches, and get tracked in real-time."
            }
            Constants.ROLE_CAR_DRIVER -> {
                binding.tvInfoTitle.text = "Driver Dispatch"
                binding.tvInfoDescription.text = "Check in, start delivery dispatches, and get tracked via GPS in real-time."
            }
        }
    }

    private fun setupCheckInButtons() {
        binding.btnDashCheckIn.setOnClickListener {
            if (!hasLocationPermission()) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
                return@setOnClickListener
            }
            performCheckIn()
        }

        binding.btnDashCheckOut.setOnClickListener {
            dashboardViewModel.checkOut(mainViewModel.userId)
        }
    }

    @SuppressLint("MissingPermission")
    private fun performCheckIn() {
        val fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                dashboardViewModel.checkIn(
                    userId = mainViewModel.userId,
                    userName = mainViewModel.userName,
                    role = mainViewModel.userRole,
                    deviceId = DeviceUtils.getDeviceId(requireContext()),
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            } else {
                Toast.makeText(requireContext(), "Unable to get location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun observeDriverStatus() {
        dashboardViewModel.isCheckedIn.observe(viewLifecycleOwner) { isCheckedIn ->
            binding.tvCheckInStatus.text = if (isCheckedIn) "Checked in" else "Not checked in"
            binding.tvCheckInStatus.setTextColor(
                resources.getColor(
                    if (isCheckedIn) R.color.success else R.color.text_secondary,
                    null
                )
            )
            // Toggle button states
            binding.btnDashCheckIn.isEnabled = !isCheckedIn
            binding.btnDashCheckOut.isEnabled = isCheckedIn

            // Update summary shift status
            binding.tvSummaryShift.text = if (isCheckedIn) "Active" else "Not started"
            binding.tvSummaryShift.setTextColor(
                resources.getColor(if (isCheckedIn) R.color.success else R.color.text_secondary, null)
            )
        }

        dashboardViewModel.dispatchSessionInfo.observe(viewLifecycleOwner) { info ->
            binding.tvDispatchDashStatus.text = info
        }

        dashboardViewModel.hasActiveDispatch.observe(viewLifecycleOwner) { isActive ->
            binding.tvDispatchDashStatus.setTextColor(
                resources.getColor(
                    if (isActive) R.color.success else R.color.text_secondary,
                    null
                )
            )
            // Update summary GPS tracking status
            binding.tvSummaryGps.text = if (isActive) "Active" else "Inactive"
            binding.tvSummaryGps.setTextColor(
                resources.getColor(if (isActive) R.color.success else R.color.text_secondary, null)
            )
        }
    }

    private fun observeDriverSummary() {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        dashboardViewModel.isCheckedIn.observe(viewLifecycleOwner) { isCheckedIn ->
            if (isCheckedIn) {
                // Show current time as check-in time approximation
                binding.tvSummaryCheckInTime.text = timeFormat.format(Date())
                binding.tvSummaryCheckInTime.setTextColor(
                    resources.getColor(R.color.success, null)
                )
            } else {
                binding.tvSummaryCheckInTime.text = "--:--"
                binding.tvSummaryCheckInTime.setTextColor(
                    resources.getColor(R.color.text_secondary, null)
                )
            }
        }
    }

    private fun setupAdminUI() {
        // Set current date
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        binding.tvAdminDate.text = dateFormat.format(Date())

        // View All navigates to Alerts
        binding.tvViewAllActivity.setOnClickListener {
            androidx.navigation.Navigation.findNavController(requireView())
                .navigate(R.id.alertsFragment)
        }
        
        // Update system status based on alerts
        dashboardViewModel.unresolvedAlertCount.observe(viewLifecycleOwner) { count ->
            if (count > 0) {
                binding.tvSystemStatus.text = "● $count unresolved alert${if (count > 1) "s" else ""} require attention"
                binding.tvSystemStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning))
            } else {
                binding.tvSystemStatus.text = "● All systems operational"
                binding.tvSystemStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
            }
        }
        
        // Observe recent activities
        observeRecentActivities()
    }
    
    private fun observeRecentActivities() {
        dashboardViewModel.recentActivities.observe(viewLifecycleOwner) { activities ->
            if (activities.isEmpty()) {
                binding.layoutActivity1.visibility = View.GONE
                binding.layoutActivity2.visibility = View.GONE
                binding.layoutActivity3.visibility = View.GONE
                return@observe
            }
            
            // Activity 1
            if (activities.isNotEmpty()) {
                binding.layoutActivity1.visibility = View.VISIBLE
                binding.tvActivity1Title.text = activities[0].title
                binding.tvActivity1Time.text = activities[0].time
                binding.viewActivity1Indicator.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), getActivityColor(activities[0].type))
                )
            } else {
                binding.layoutActivity1.visibility = View.GONE
            }
            
            // Activity 2
            if (activities.size > 1) {
                binding.layoutActivity2.visibility = View.VISIBLE
                binding.tvActivity2Title.text = activities[1].title
                binding.tvActivity2Time.text = activities[1].time
                binding.viewActivity2Indicator.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), getActivityColor(activities[1].type))
                )
            } else {
                binding.layoutActivity2.visibility = View.GONE
            }
            
            // Activity 3
            if (activities.size > 2) {
                binding.layoutActivity3.visibility = View.VISIBLE
                binding.tvActivity3Title.text = activities[2].title
                binding.tvActivity3Time.text = activities[2].time
                binding.viewActivity3Indicator.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), getActivityColor(activities[2].type))
                )
            } else {
                binding.layoutActivity3.visibility = View.GONE
            }
        }
    }
    
    private fun getActivityColor(type: ActivityType): Int {
        return when (type) {
            ActivityType.ALERT -> R.color.error
            ActivityType.CHECK_IN -> R.color.success
            ActivityType.DISPATCH -> R.color.primary
        }
    }

    private fun observeAdminStats() {
        dashboardViewModel.activeDispatchCount.observe(viewLifecycleOwner) { count ->
            binding.tvStatDispatches.text = count.toString()
        }
        dashboardViewModel.unresolvedAlertCount.observe(viewLifecycleOwner) { count ->
            binding.tvStatAlerts.text = count.toString()
        }
        dashboardViewModel.checkedInCount.observe(viewLifecycleOwner) { count ->
            binding.tvStatCheckedIn.text = count.toString()
        }
    }

    /**
     * Observes employee statuses and populates the Employee Status card.
     * Shows up to 5 employees with their online/dispatching status.
     * Admin/CEO can see if technicians/drivers are online or dispatching.
     */
    private fun observeEmployeeStatuses() {
        dashboardViewModel.employeeStatuses.observe(viewLifecycleOwner) { employees ->
            if (employees.isEmpty()) {
                binding.tvEmployeeStatusEmpty.visibility = View.VISIBLE
                hideAllEmployeeRows()
                return@observe
            }

            binding.tvEmployeeStatusEmpty.visibility = View.GONE

            // Data arrays for binding
            val layouts = listOf(
                binding.layoutEmployee1,
                binding.layoutEmployee2,
                binding.layoutEmployee3,
                binding.layoutEmployee4,
                binding.layoutEmployee5
            )
            val dots = listOf(
                binding.viewEmployee1StatusDot,
                binding.viewEmployee2StatusDot,
                binding.viewEmployee3StatusDot,
                binding.viewEmployee4StatusDot,
                binding.viewEmployee5StatusDot
            )
            val names = listOf(
                binding.tvEmployee1Name,
                binding.tvEmployee2Name,
                binding.tvEmployee3Name,
                binding.tvEmployee4Name,
                binding.tvEmployee5Name
            )
            val roles = listOf(
                binding.tvEmployee1Role,
                binding.tvEmployee2Role,
                binding.tvEmployee3Role,
                binding.tvEmployee4Role,
                binding.tvEmployee5Role
            )
            val statuses = listOf(
                binding.tvEmployee1Status,
                binding.tvEmployee2Status,
                binding.tvEmployee3Status,
                binding.tvEmployee4Status,
                binding.tvEmployee5Status
            )
            val dividers = listOf(
                binding.dividerEmployee1,
                binding.dividerEmployee2,
                binding.dividerEmployee3,
                binding.dividerEmployee4,
                binding.dividerEmployee4 // last one has no next divider, reuse
            )

            val maxItems = minOf(employees.size, 5)
            for (i in 0 until 5) {
                if (i < maxItems) {
                    val emp = employees[i]
                    layouts[i].visibility = View.VISIBLE
                    names[i].text = emp.displayName
                    roles[i].text = emp.role

                    // Set status text and color
                    when {
                        emp.isDispatching -> {
                            statuses[i].text = "Dispatching"
                            statuses[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                            statuses[i].backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.success)
                            dots[i].backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.success)
                        }
                        emp.isOnline -> {
                            statuses[i].text = "Online"
                            statuses[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                            statuses[i].backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary)
                            dots[i].backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary)
                        }
                        else -> {
                            statuses[i].text = "Offline"
                            statuses[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                            statuses[i].backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary_lightest)
                            dots[i].backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.text_secondary)
                        }
                    }

                    // Show divider between items (not after last)
                    if (i < maxItems - 1 && i < dividers.size) {
                        dividers[i].visibility = View.VISIBLE
                    } else if (i < dividers.size) {
                        dividers[i].visibility = View.GONE
                    }
                } else {
                    layouts[i].visibility = View.GONE
                    if (i < dividers.size) dividers[i].visibility = View.GONE
                }
            }
        }
    }

    private fun hideAllEmployeeRows() {
        binding.layoutEmployee1.visibility = View.GONE
        binding.layoutEmployee2.visibility = View.GONE
        binding.layoutEmployee3.visibility = View.GONE
        binding.layoutEmployee4.visibility = View.GONE
        binding.layoutEmployee5.visibility = View.GONE
        binding.dividerEmployee1.visibility = View.GONE
        binding.dividerEmployee2.visibility = View.GONE
        binding.dividerEmployee3.visibility = View.GONE
        binding.dividerEmployee4.visibility = View.GONE
    }

    private fun observeCheckInActions() {
        dashboardViewModel.checkInState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progressCheckIn.visibility = View.VISIBLE
                    binding.btnDashCheckIn.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressCheckIn.visibility = View.GONE
                    Toast.makeText(requireContext(), "Checked in successfully", Toast.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    binding.progressCheckIn.visibility = View.GONE
                    binding.btnDashCheckIn.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        dashboardViewModel.checkOutState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progressCheckIn.visibility = View.VISIBLE
                    binding.btnDashCheckOut.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressCheckIn.visibility = View.GONE
                    Toast.makeText(requireContext(), "Checked out successfully", Toast.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    binding.progressCheckIn.visibility = View.GONE
                    binding.btnDashCheckOut.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
