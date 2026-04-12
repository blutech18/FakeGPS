package com.nodare.geosec.presentation.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.nodare.geosec.R
import com.nodare.geosec.databinding.FragmentProfileBinding
import com.nodare.geosec.presentation.auth.LoginActivity
import com.nodare.geosec.presentation.dashboard.MainViewModel
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: ProfileViewModel by viewModels()
    private var isEditMode = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeUser()
        setupEditButton()
        setupSaveButton()
        setupLogoutButton()
        observeUpdateState()
    }

    private fun observeUser() {
        mainViewModel.currentUser.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Success -> {
                    val user = state.data
                    binding.etProfileName.setText(user.displayName)
                    binding.etProfileEmail.setText(user.email)
                    binding.tvProfileUserId.text = user.id
                    binding.tvProfileRole.text = user.role

                    // Set role badge color
                    val badgeColor = when (user.role) {
                        Constants.ROLE_CEO -> R.color.severity_critical
                        Constants.ROLE_ADMIN -> R.color.primary
                        Constants.ROLE_TECHNICIAN -> R.color.accent
                        Constants.ROLE_CAR_DRIVER -> R.color.success
                        else -> R.color.primary
                    }
                    binding.tvProfileRole.backgroundTintList = 
                        ContextCompat.getColorStateList(requireContext(), badgeColor)
                }
                is Resource.Error -> {
                    Toast.makeText(requireContext(), "Error loading profile", Toast.LENGTH_SHORT).show()
                }
                is Resource.Loading -> { }
            }
        }
    }

    private fun setupEditButton() {
        binding.btnEditProfile.setOnClickListener {
            if (isEditMode) {
                // Cancel edit mode
                setEditMode(false)
                mainViewModel.loadCurrentUser() // Reload original data
            } else {
                // Enter edit mode
                setEditMode(true)
            }
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        // Clear errors when typing
        binding.etProfileName.doAfterTextChanged {
            binding.tilProfileName.error = null
        }
        binding.etProfileEmail.doAfterTextChanged {
            binding.tilProfileEmail.error = null
        }
    }

    private fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        binding.etProfileName.isEnabled = enabled
        binding.etProfileEmail.isEnabled = enabled
        binding.btnSaveProfile.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.btnEditProfile.text = if (enabled) "Cancel" else "Edit"
        binding.btnEditProfile.icon = if (enabled) null else ContextCompat.getDrawable(requireContext(), R.drawable.ic_edit)

        if (enabled) {
            binding.etProfileName.requestFocus()
        }
    }

    private fun saveProfile() {
        val name = binding.etProfileName.text.toString().trim()
        val email = binding.etProfileEmail.text.toString().trim()

        // Clear previous errors
        binding.tilProfileName.error = null
        binding.tilProfileEmail.error = null

        // Validate name
        if (name.isBlank()) {
            binding.tilProfileName.error = "Name is required"
            binding.etProfileName.requestFocus()
            return
        }
        if (name.length < 2) {
            binding.tilProfileName.error = "Name must be at least 2 characters"
            binding.etProfileName.requestFocus()
            return
        }

        // Validate email
        if (email.isBlank()) {
            binding.tilProfileEmail.error = "Email is required"
            binding.etProfileEmail.requestFocus()
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilProfileEmail.error = "Please enter a valid email address"
            binding.etProfileEmail.requestFocus()
            return
        }

        viewModel.updateProfile(mainViewModel.userId, name, email)
    }

    private fun observeUpdateState() {
        viewModel.updateState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progressProfile.visibility = View.VISIBLE
                    binding.btnSaveProfile.isEnabled = false
                    binding.btnEditProfile.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressProfile.visibility = View.GONE
                    binding.btnSaveProfile.isEnabled = true
                    binding.btnEditProfile.isEnabled = true
                    Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    setEditMode(false)
                    mainViewModel.loadCurrentUser() // Refresh user data
                }
                is Resource.Error -> {
                    binding.progressProfile.visibility = View.GONE
                    binding.btnSaveProfile.isEnabled = true
                    binding.btnEditProfile.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupLogoutButton() {
        binding.btnLogout.setOnClickListener {
            mainViewModel.logout()
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
