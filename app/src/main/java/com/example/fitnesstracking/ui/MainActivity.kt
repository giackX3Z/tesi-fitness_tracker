package com.example.fitnesstracking.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.fitnesstracking.R
import com.example.fitnesstracking.databinding.ActivityMainBinding
import com.example.fitnesstracking.other.Constants.ACTION_SHOW_TRACKING_FRAGMENT
import com.example.fitnesstracking.ui.fragments.SetupFragment
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    //TEST TO SEE DAGGER WORKING
    //@Inject
    //lateinit var runDAO: RunDAO
    //Log.d("RUN DAO", "RUNDAO ${runDAO.hashCode()}")

    private lateinit var binding: ActivityMainBinding
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var navController: androidx.navigation.NavController

    // List of fragment to hide logout button
    private val fragmentsWithoutLogout = setOf(
        R.id.setupFragment,
        R.id.registerFragment,
        R.id.trackingFragment
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navigateToTrackingFragmentIfNeeded(intent)

        setSupportActionBar(binding.toolbar)

        binding.bottomNavigationView.setOnItemReselectedListener {
            /*NO-OP*/
        }

        // Initialization of navHostFragment e navController
        navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigationView.setupWithNavController(navController)

        // Listener to monitor destination change
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in fragmentsWithoutLogout) {
                invalidateOptionsMenu() // Aggiorna il menu per nascondere il pulsante di logout
            } else {
                invalidateOptionsMenu() // Aggiorna il menu per mostrare il pulsante di logout
            }
        }


        //Manage visibility of BottomNavigationView
        navHostFragment.findNavController()
            .addOnDestinationChangedListener{_, destination, _ ->
                when(destination.id){
                    R.id.settingsFragment, R.id.runFragment, R.id.statisticFragment ->
                        binding.bottomNavigationView.visibility = View.VISIBLE
                    else -> binding.bottomNavigationView.visibility = View.GONE
                }
            }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navigateToTrackingFragmentIfNeeded(intent)
    }

    private fun navigateToTrackingFragmentIfNeeded(intent:Intent?){
        if(intent?.action == ACTION_SHOW_TRACKING_FRAGMENT) {
            navHostFragment.findNavController().navigate(R.id.action_global_trackingFragment)
        }
    }

    // Creating menu with logout button
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.logout_menu, menu)
        return true
    }

    // Modify the base menu to the current fragment
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val logoutItem = menu?.findItem(R.id.action_logout)
        // Show or hide logout icon
        val currentDestination = navController.currentDestination
        logoutItem?.isVisible =
            !(currentDestination != null && currentDestination.id in fragmentsWithoutLogout)
        return super.onPrepareOptionsMenu(menu)
    }

    // Method to manage items on menu
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                // Logout when icon is pressed
                FirebaseAuth.getInstance().signOut()

                // Navigate to setup (login) anc clear back stack
                navHostFragment.findNavController().navigate(
                    R.id.setupFragment,
                    null,
                    NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .build()
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

}