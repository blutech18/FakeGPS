package com.nodare.geosec.presentation.dispatch

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nodare.geosec.R
import com.nodare.geosec.databinding.FragmentDispatchBinding
import com.nodare.geosec.databinding.DialogDispatchKeyBinding
import com.nodare.geosec.presentation.common.adapter.DispatchHistoryAdapter
import com.nodare.geosec.presentation.dashboard.MainViewModel
import com.nodare.geosec.services.gps.GpsTrackingService
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DispatchFragment : Fragment() {

    private var _binding: FragmentDispatchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DispatchViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private var currentSessionId: String? = null
    private var currentDispatchKey: String? = null
    private lateinit var historyAdapter: DispatchHistoryAdapter
    private var roleConfigured = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            checkBackgroundLocationAndStart()
        } else {
            Toast.makeText(requireContext(), "Location permission is required for dispatch", Toast.LENGTH_LONG).show()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkNotificationPermissionAndStart()
        } else {
            Toast.makeText(requireContext(), "Background location is needed for tracking", Toast.LENGTH_LONG).show()
            startDispatch()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        startDispatch()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDispatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHistoryRecyclerView()

        // Check if user is already loaded (navigating from another fragment)
        if (mainViewModel.isUserLoaded && !roleConfigured) {
            roleConfigured = true
            configureForRole(mainViewModel.isAdmin)
        }

        mainViewModel.currentUser.observe(viewLifecycleOwner) { state ->
            if (state is Resource.Success && !roleConfigured) {
                roleConfigured = true
                configureForRole(mainViewModel.isAdmin)
            }
        }
    }

    private fun configureForRole(isAdmin: Boolean) {
        if (isAdmin) {
            // CEO/Admin: monitoring view — hide own dispatch controls, show ALL sessions
            binding.cardCheckInWarning.visibility = View.GONE
            binding.btnStartDispatch.visibility = View.GONE
            binding.btnEndDispatch.visibility = View.GONE

            if (mainViewModel.isCeo) {
                binding.tvDispatchStatus.text = "Dispatch Overview"
                binding.tvSessionInfo.text = "Executive view of all dispatch operations"
            } else {
                binding.tvDispatchStatus.text = "All Dispatch Sessions"
                binding.tvSessionInfo.text = "Monitoring all active and past sessions"
            }

            binding.layoutHistory.visibility = View.VISIBLE
            viewModel.observeAllSessions()
            observeAdminHistory()
        } else {
            // Technician / Car Driver: own dispatch controls + own history
            val role = mainViewModel.userRole
            if (role == Constants.ROLE_CAR_DRIVER) {
                binding.btnStartDispatch.text = "Start Delivery"
                binding.btnEndDispatch.text = "End Delivery"
            } else {
                binding.btnStartDispatch.text = "Start Dispatch"
                binding.btnEndDispatch.text = "End Dispatch"
            }

            setupButtons()
            observeStates()
            viewModel.loadActiveSession(mainViewModel.userId)
            viewModel.observeUserHistory(mainViewModel.userId)
            binding.layoutHistory.visibility = View.VISIBLE
            observeUserHistory()
        }
    }

    private fun setupHistoryRecyclerView() {
        historyAdapter = DispatchHistoryAdapter()
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = historyAdapter
    }

    private fun setupButtons() {
        binding.btnStartDispatch.setOnClickListener {
            if (!hasLocationPermission()) {
                requestLocationPermission()
                return@setOnClickListener
            }
            checkBackgroundLocationAndStart()
        }

        binding.btnEndDispatch.setOnClickListener {
            currentSessionId?.let { sessionId ->
                // Show dispatch key confirmation dialog before ending
                showDispatchKeyDialog(sessionId)
            }
        }

        binding.btnViewDispatchKey.setOnClickListener {
            currentDispatchKey?.let { key ->
                showDispatchKeyInfo(key)
            } ?: Toast.makeText(requireContext(), "No dispatch key available", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shows a dialog prompting the user to enter their dispatch confirmation code
     * before the dispatch/delivery can be ended.
     */
    private fun showDispatchKeyDialog(sessionId: String) {
        val dialogBinding = DialogDispatchKeyBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Delivery Completion")
            .setView(dialogBinding.root)
            .setPositiveButton("Confirm") { _, _ ->
                val enteredKey = dialogBinding.etDispatchKey.text.toString().trim()
                if (enteredKey.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter the confirmation code", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                endDispatch(sessionId, enteredKey)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    /**
     * Shows the dispatch key to the user after starting a dispatch.
     * The user must remember or copy this key to end the dispatch later.
     */
    private fun showDispatchKeyInfo(dispatchKey: String) {
        if (dispatchKey.isBlank()) {
            Toast.makeText(requireContext(), "Dispatch key is not available", Toast.LENGTH_SHORT).show()
            return
        }

        // Build a custom view to show the key prominently
        val keyTextView = android.widget.TextView(requireContext()).apply {
            text = dispatchKey
            textSize = 32f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(resources.getColor(R.color.primary_dark, requireContext().theme))
            setPadding(0, 24, 0, 24)
            setTextIsSelectable(true)
        }

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)

            val label = android.widget.TextView(requireContext()).apply {
                text = "Your dispatch confirmation code is:"
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_secondary, requireContext().theme))
            }
            addView(label)
            addView(keyTextView)

            val note = android.widget.TextView(requireContext()).apply {
                text = "You will need to enter this code when you end your delivery. Please remember or save this code."
                textSize = 13f
                setTextColor(resources.getColor(R.color.text_secondary, requireContext().theme))
            }
            addView(note)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Dispatch Key Generated")
            .setView(container)
            .setPositiveButton("Copy & Close") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Dispatch Key", dispatchKey)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Dispatch key copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .setCancelable(false)
            .show()
    }

    private fun checkBackgroundLocationAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            return
        }
        checkNotificationPermissionAndStart()
    }

    private fun checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startDispatch()
    }

    @SuppressLint("MissingPermission")
    private fun startDispatch() {
        val fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                viewModel.startDispatch(
                    userId = mainViewModel.userId,
                    userName = mainViewModel.userName,
                    role = mainViewModel.userRole,
                    lat = location.latitude,
                    lng = location.longitude
                )
            } else {
                Toast.makeText(requireContext(), "Unable to get location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun endDispatch(sessionId: String, enteredKey: String) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                viewModel.endDispatch(sessionId, location.latitude, location.longitude, enteredKey)
            } else {
                viewModel.endDispatch(sessionId, 0.0, 0.0, enteredKey)
            }
        }
    }

    private fun startGpsTrackingService(sessionId: String) {
        // Verify location permission before starting the foreground service
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "Location permission required for GPS tracking", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val intent = Intent(requireContext(), GpsTrackingService::class.java).apply {
                action = GpsTrackingService.ACTION_START
                putExtra(GpsTrackingService.EXTRA_USER_ID, mainViewModel.userId)
                putExtra(GpsTrackingService.EXTRA_USER_NAME, mainViewModel.userName)
                putExtra(GpsTrackingService.EXTRA_SESSION_ID, sessionId)
            }
            ContextCompat.startForegroundService(requireContext(), intent)
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Unable to start GPS tracking: missing permissions", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopGpsTrackingService() {
        val intent = Intent(requireContext(), GpsTrackingService::class.java).apply {
            action = GpsTrackingService.ACTION_STOP
        }
        requireContext().startService(intent)
    }

    private fun observeStates() {
        viewModel.isCheckedIn.observe(viewLifecycleOwner) { isCheckedIn ->
            binding.cardCheckInWarning.visibility = if (isCheckedIn) View.GONE else View.VISIBLE
            // Only enable start if checked in AND no active session
            if (!isCheckedIn && currentSessionId == null) {
                binding.btnStartDispatch.isEnabled = false
            } else if (isCheckedIn && currentSessionId == null) {
                binding.btnStartDispatch.isEnabled = true
            }
        }

        viewModel.startState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnStartDispatch.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    val result = state.data
                    currentSessionId = result.sessionId
                    currentDispatchKey = result.dispatchKey
                    updateUI(isActive = true)
                    startGpsTrackingService(result.sessionId)
                    Toast.makeText(requireContext(), "Dispatch started", Toast.LENGTH_SHORT).show()
                    // Auto-copy dispatch key to clipboard
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Dispatch Key", result.dispatchKey)
                    clipboard.setPrimaryClip(clip)
                    // Show the dispatch key to the user
                    showDispatchKeyInfo(result.dispatchKey)
                    viewModel.resetStartState()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnStartDispatch.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetStartState()
                }
                null -> {
                    // State has been reset, do nothing
                }
            }
        }

        viewModel.endState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> binding.progressBar.visibility = View.VISIBLE
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    currentSessionId = null
                    currentDispatchKey = null
                    updateUI(isActive = false)
                    stopGpsTrackingService()
                    Toast.makeText(requireContext(), "Dispatch ended successfully", Toast.LENGTH_SHORT).show()
                    viewModel.fetchUserHistory(mainViewModel.userId)
                    viewModel.resetEndState()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetEndState()
                }
                null -> {
                    // State has been reset, do nothing
                }
            }
        }

        viewModel.activeSession.observe(viewLifecycleOwner) { session ->
            if (session != null) {
                currentSessionId = session.id
                currentDispatchKey = session.dispatchKey
                updateUI(isActive = true)

                // Show formatted dispatch reference instead of raw ID
                val displayRef = if (session.dispatchKey.isNotBlank()) {
                    session.dispatchKey.take(8).uppercase()
                } else {
                    session.id.take(6).uppercase()
                }
                val timeStr = session.startTime?.toDate()?.let { date ->
                    java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(date)
                } ?: ""
                binding.tvSessionInfo.text = "Dispatch #$displayRef${if (timeStr.isNotEmpty()) " · Started $timeStr" else ""}"

                // Restart GPS service if it's not already running
                if (!isGpsServiceRunning()) {
                    startGpsTrackingService(session.id)
                }
            } else {
                updateUI(isActive = false)
                binding.tvSessionInfo.text = "No active dispatch session"
            }
        }
    }

    private fun updateUI(isActive: Boolean) {
        val checkedIn = viewModel.isCheckedIn.value ?: false
        binding.btnStartDispatch.isEnabled = !isActive && checkedIn
        binding.btnEndDispatch.isEnabled = isActive
        binding.btnViewDispatchKey.visibility = if (isActive) View.VISIBLE else View.GONE
        val label = if (mainViewModel.userRole == Constants.ROLE_CAR_DRIVER) "Delivery" else "Dispatch"
        binding.tvDispatchStatus.text = if (isActive) "$label: ACTIVE" else "$label: INACTIVE"
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

    private fun observeUserHistory() {
        viewModel.userSessions.observe(viewLifecycleOwner) { sessions ->
            historyAdapter.submitList(sessions)
            binding.tvHistoryEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun observeAdminHistory() {
        viewModel.allSessions.observe(viewLifecycleOwner) { sessions ->
            historyAdapter.submitList(sessions)
            binding.tvHistoryEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    @Suppress("DEPRECATION")
    private fun isGpsServiceRunning(): Boolean {
        val manager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (GpsTrackingService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
