package com.example.fitnesstracking.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.fitnesstracking.R
import com.example.fitnesstracking.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import timber.log.Timber

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Firebase Auth instance
        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            // Get user data
            fetchUserData(user.uid)
        } else {
            // User not authenticated
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
        }

        binding.btnApplyChanges.setOnClickListener {
            val newName = binding.etName.text.toString().trim()
            val newWeight = binding.etWeight.text.toString().trim()

            if (user != null) {
                updateUserProfile(user, newName, newWeight)
            } else {
                // User not auth
                Timber.d("User not logged in")
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun fetchUserData(userId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    // Get data
                    val name = document.getString("name") ?: ""
                    val weight = document.getString("weight") ?: ""

                    // Set values in EditText
                    binding.etName.setText(name)
                    binding.etWeight.setText(weight)
                } else {
                    Toast.makeText(requireContext(), "No such document", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to fetch data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUserProfile(user: FirebaseUser, newName: String, newWeight: String) {
        val updates = mutableMapOf<String, Any>()

        if (newName.isNotEmpty()) {
            updates["name"] = newName
        }
        if (newWeight.isNotEmpty()) {
            updates["weight"] = newWeight
        }

        if (updates.isNotEmpty()) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(user.uid)
                .update(updates)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(requireContext(), "Please enter a value to update", Toast.LENGTH_SHORT).show()
        }
    }



}