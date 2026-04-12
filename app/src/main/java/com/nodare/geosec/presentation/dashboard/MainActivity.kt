package com.nodare.geosec.presentation.dashboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.nodare.geosec.R
import com.nodare.geosec.databinding.ActivityMainBinding
import com.nodare.geosec.presentation.auth.LoginActivity
import com.nodare.geosec.util.Constants
import com.nodare.geosec.util.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var navController: NavController
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private var isAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        setupNavigation()
        setupDrawer()
        observeUser()
        viewModel.loadCurrentUser()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Update custom toolbar title on navigation
        val toolbarTitle = binding.toolbar.findViewById<android.widget.TextView>(R.id.toolbarTitle)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            toolbarTitle?.text = destination.label
        }
    }

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        drawerToggle.drawerArrowDrawable.color = resources.getColor(R.color.white, theme)

        // Set drawer width to 80% of the default (reduce by 20%)
        val screenWidth = resources.displayMetrics.widthPixels
        val drawerWidth = (screenWidth * 0.64).toInt() // ~80% of typical 80% screen width
        binding.navView.layoutParams.width = drawerWidth

        // Prevent NavigationView from consuming status bar insets itself,
        // and instead apply the top inset as padding on the drawer header
        ViewCompat.setOnApplyWindowInsetsListener(binding.navView) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            // Remove NavigationView's own top padding so it doesn't double-pad
            view.setPadding(0, 0, 0, 0)
            // Apply status bar padding to the header so content sits below the status bar
            val headerView = binding.navView.getHeaderView(0)
            headerView?.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        // Lock drawer by default until role is determined
        binding.drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    private fun setupForAdmin() {
        // Enable drawer
        binding.drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
        drawerToggle.isDrawerIndicatorEnabled = true
        drawerToggle.syncState()

        // Hide bottom nav
        binding.bottomNav.visibility = View.GONE
        binding.bottomNavDivider.visibility = View.GONE

        // Remove bottom margin from fragment container
        val navHostFragment = findViewById<View>(R.id.nav_host_fragment)
        val params = navHostFragment.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
        params.bottomMargin = 0
        navHostFragment.layoutParams = params

        // Configure app bar with drawer
        val appBarConfig = AppBarConfiguration(
            setOf(
                R.id.dashboardFragment,
                R.id.checkInFragment,
                R.id.trackingFragment,
                R.id.alertsFragment,
                R.id.profileFragment
            ),
            binding.drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfig)
        binding.navView.setupWithNavController(navController)

        // Re-apply white tint after setupActionBarWithNavController overrides the icon
        drawerToggle.drawerArrowDrawable.color = resources.getColor(R.color.white, theme)
        binding.toolbar.navigationIcon?.setTint(resources.getColor(R.color.white, theme))

        // Ensure white nav icon persists on destination changes
        navController.addOnDestinationChangedListener { _, _, _ ->
            drawerToggle.drawerArrowDrawable.color = resources.getColor(R.color.white, theme)
            binding.toolbar.navigationIcon?.setTint(resources.getColor(R.color.white, theme))
        }

        // Close drawer on item selection
        binding.navView.setNavigationItemSelectedListener { item ->
            val handled = NavigationUI.onNavDestinationSelected(item, navController)
            if (handled) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            handled
        }
    }

    private fun setupForDriver() {
        // Lock drawer closed
        binding.drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        drawerToggle.isDrawerIndicatorEnabled = false
        binding.toolbar.navigationIcon = null
        drawerToggle.syncState()

        // Add proper start padding for toolbar content since there's no hamburger icon
        val toolbarContent = binding.toolbar.getChildAt(0)
        if (toolbarContent is ViewGroup) {
            val startPadding = (12 * resources.displayMetrics.density).toInt()
            toolbarContent.setPadding(startPadding, toolbarContent.paddingTop, toolbarContent.paddingRight, toolbarContent.paddingBottom)
        }

        // Show bottom nav
        binding.bottomNav.visibility = View.VISIBLE
        binding.bottomNavDivider.visibility = View.VISIBLE

        // Add bottom margin for fragment container
        val navHostFragment = findViewById<View>(R.id.nav_host_fragment)
        val params = navHostFragment.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
        params.bottomMargin = (56 * resources.displayMetrics.density).toInt()
        navHostFragment.layoutParams = params

        // Configure app bar without drawer
        val appBarConfig = AppBarConfiguration(
            setOf(
                R.id.dashboardFragment,
                R.id.equipmentFragment,
                R.id.dispatchFragment,
                R.id.profileFragment
            )
        )
        setupActionBarWithNavController(navController, appBarConfig)
        binding.bottomNav.setupWithNavController(navController)

        // Ensure no nav icon shows and back arrows stay white on destination changes
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isTopLevel = appBarConfig.topLevelDestinations.contains(destination.id)
            if (isTopLevel) {
                binding.toolbar.navigationIcon = null
            } else {
                binding.toolbar.navigationIcon?.setTint(resources.getColor(R.color.white, theme))
            }
        }
    }

    private fun observeUser() {
        viewModel.currentUser.observe(this) { state ->
            when (state) {
                is Resource.Success -> {
                    val user = state.data
                    configureMenuForRole(user.role)
                }
                is Resource.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is Resource.Loading -> { }
            }
        }
    }

    private fun configureMenuForRole(role: String) {
        isAdmin = role == Constants.ROLE_CEO || role == Constants.ROLE_ADMIN

        if (isAdmin) {
            setupForAdmin()
        } else {
            setupForDriver()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                showLogoutConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Logout") { _, _ ->
                viewModel.logout()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        return if (isAdmin) {
            NavigationUI.navigateUp(navController, binding.drawerLayout) || super.onSupportNavigateUp()
        } else {
            navController.navigateUp() || super.onSupportNavigateUp()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun isTouchOnEditText(ev: MotionEvent): Boolean {
        val root = window.decorView.rootView
        return isTouchOnEditTextView(root, ev)
    }

    private fun isTouchOnEditTextView(view: View, ev: MotionEvent): Boolean {
        if (view is android.widget.EditText || view is com.google.android.material.textfield.TextInputLayout) {
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val rect = android.graphics.Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
            if (rect.contains(ev.x.toInt(), ev.y.toInt())) return true
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (isTouchOnEditTextView(view.getChildAt(i), ev)) return true
            }
        }
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is android.widget.EditText && !isTouchOnEditText(ev)) {
                v.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
