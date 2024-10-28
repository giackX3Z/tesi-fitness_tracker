package com.example.fitnesstracking.ui.fragments

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitnesstracking.R
import com.example.fitnesstracking.adapters.RunAdapter
import com.example.fitnesstracking.databinding.FragmentRunBinding
import com.example.fitnesstracking.data.Run
import com.example.fitnesstracking.other.Constants.REQUEST_CODE_LOCATION_PERMISSION
import com.example.fitnesstracking.other.SortType
import com.example.fitnesstracking.other.TrackingUtility
import dagger.hilt.android.AndroidEntryPoint
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions

@AndroidEntryPoint
class RunFragment : Fragment(R.layout.fragment_run), EasyPermissions.PermissionCallbacks {

    private lateinit var runAdapter: RunAdapter
    private var _binding: FragmentRunBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout using View Binding
        _binding = FragmentRunBinding.inflate(inflater, container, false)

        // Change backPressed button
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Reduce app to icon
                requireActivity().moveTaskToBack(true)
            }
        })

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestPermission()
        setupRecyclerView()
        setupSpinner()


        // Access the fab button using View Binding and set an onClickListener
        binding.fab.setOnClickListener {
            // Handle the FAB button click event here
            findNavController().navigate(R.id.action_runFragment_to_trackingFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

/*-----------------------------METHODS TO REQUEST PERMISSION--------------------------------------*/

    private fun requestPermission(){
        if(TrackingUtility.hasLocationPermission(requireContext())){
            return
        }
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
            EasyPermissions.requestPermissions(
                this,
                "You need to accept location permission to use this app." ,
                REQUEST_CODE_LOCATION_PERMISSION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        else{
            EasyPermissions.requestPermissions(
                this,
                "You need to accept location permission to use this app." ,
                REQUEST_CODE_LOCATION_PERMISSION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {}

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if(EasyPermissions.somePermissionPermanentlyDenied(this, perms)){
            AppSettingsDialog.Builder(this).build().show()
        }
        else{
            requestPermission()
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    /*-------------------------METHODS TO SETUP RECYCLERVIEW AND FILTERS--------------------------*/

    private fun setupRecyclerView() {
        runAdapter = RunAdapter(::showDeleteConfirmationDialog)
        binding.rvRuns.apply {
            adapter = runAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupSpinner() {
        val sortOptions = arrayOf("Date", "Running Time", "Distance", "Average Speed", "Calories Burned", "Steps")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spFilter.adapter = adapter
        binding.spFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val sortType = when (position) {
                    0 -> SortType.DATE
                    1 -> SortType.RUNNING_TIME
                    2 -> SortType.DISTANCE
                    3 -> SortType.AVG_SPEED
                    4 -> SortType.CALORIES_BURNED
                    5 -> SortType.STEPS
                    else -> SortType.DATE
                }
                runAdapter.updateSortType(sortType)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }


    /*---------------------------------METHODS TO DELETE RUN--------------------------------------*/
    private fun showDeleteConfirmationDialog(run: Run) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm delete")
            .setMessage("Do you really want to delete this run?")
            .setPositiveButton("Delete") { _, _ ->
                runAdapter.deleteRun(run)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}