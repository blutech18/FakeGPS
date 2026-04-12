package com.nodare.geosec.presentation.checkin

import android.Manifest
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
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.LocationServices
import com.nodare.geosec.R
import com.nodare.geosec.databinding.FragmentCheckinBinding
import com.nodare.geosec.presentation.common.adapter.CheckInLogAdapter
import com.nodare.geosec.presentation.dashboard.MainViewModel
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.DeviceUtils
import com.nodare.geosec.util.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CheckInFragment : Fragment() {

    private var _binding: FragmentCheckinBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CheckInViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var logAdapter: CheckInLogAdapter
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
        _binding = FragmentCheckinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check if user is already loaded (navigating from another fragment)
        if (mainViewModel.isUserLoaded && !roleConfigured) {
            configureForRole(mainViewModel.isAdmin)
        }

        mainViewModel.currentUser.observe(viewLifecycleOwner) { state ->
            if (state is Resource.Success && !roleConfigured) {
                configureForRole(mainViewModel.isAdmin)
            }
        }
    }

    private fun configureForRole(isAdmin: Boolean) {
        if (isAdmin) {
            // CEO/Admin: hide own check-in card, show employee logs
            binding.cardOwnCheckIn.visibility = View.GONE
            binding.layoutAdminLogs.visibility = View.VISIBLE
            if (!roleConfigured) {
                roleConfigured = true
                setupAdminLogs()
            }
        } else {
            // Technician/Car Driver: show own check-in card, hide logs
            binding.cardOwnCheckIn.visibility = View.VISIBLE
            binding.layoutAdminLogs.visibility = View.GONE
            if (!roleConfigured) {
                roleConfigured = true
                setupButtons()
                observeStates()
                viewModel.checkActiveSession(mainViewModel.userId)
            }
        }
    }

    private fun setupAdminLogs() {
        logAdapter = CheckInLogAdapter()
        binding.recyclerLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLogs.adapter = logAdapter

        setupRoleFilter()
        viewModel.observeAllLogs()
        viewModel.checkInLogs.observe(viewLifecycleOwner) { logs ->
            logAdapter.submitList(logs)
            binding.tvLogsEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupRoleFilter() {
        binding.chipGroupRoleFilter.setOnCheckedChangeListener { _, checkedId ->
            val roleFilter = when (checkedId) {
                R.id.chipCeo -> Constants.ROLE_CEO
                R.id.chipAdmin -> Constants.ROLE_ADMIN
                R.id.chipTechnician -> Constants.ROLE_TECHNICIAN
                R.id.chipCarDriver -> Constants.ROLE_CAR_DRIVER
                else -> null // "All" chip or no selection
            }
            viewModel.filterByRole(roleFilter)
        }
    }

    private fun setupButtons() {
        binding.btnCheckIn.setOnClickListener {
            if (!hasLocationPermission()) {
                requestLocationPermission()
                return@setOnClickListener
            }
            performCheckIn()
        }

        binding.btnCheckOut.setOnClickListener {
            viewModel.checkOut(mainViewModel.userId)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun performCheckIn() {
        val fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                viewModel.checkIn(
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

    private fun observeStates() {
        viewModel.checkInState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnCheckIn.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCheckIn.isEnabled = true
                    Toast.makeText(requireContext(), "Checked in successfully", Toast.LENGTH_SHORT).show()
                    viewModel.checkActiveSession(mainViewModel.userId)
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCheckIn.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.checkOutState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> binding.progressBar.visibility = View.VISIBLE
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Checked out successfully", Toast.LENGTH_SHORT).show()
                    viewModel.checkActiveSession(mainViewModel.userId)
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.isCheckedIn.observe(viewLifecycleOwner) { isCheckedIn ->
            binding.btnCheckIn.isEnabled = !isCheckedIn
            binding.btnCheckOut.isEnabled = isCheckedIn
            binding.tvStatus.text = if (isCheckedIn) "Status: Checked In" else "Status: Checked Out"
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
