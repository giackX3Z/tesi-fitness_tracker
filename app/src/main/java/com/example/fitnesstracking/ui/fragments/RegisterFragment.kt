package com.example.fitnesstracking.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.fitnesstracking.R
import com.example.fitnesstracking.databinding.FragmentRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore


class RegisterFragment : Fragment(R.layout.fragment_register) {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    // Firebase Auth
    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout using View Binding
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Firebase Auth Initialization
        auth = FirebaseAuth.getInstance()

        binding.tvContinue.setOnClickListener {

            val email = binding.etMail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val weight = binding.etWeight.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty() && email.isNotEmpty() && weight.isNotEmpty()) {
                registerUser(email, password, weight)
            } else {
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_setupFragment)
        }

    }

    private fun registerUser(email: String, password: String, weight: String) {
        // Firebase Auth: Creating user with mail and password
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // user created, saving data on db
                    val userId = auth.currentUser?.uid

                    val userMap = hashMapOf(
                        "email" to email,
                        "weight" to weight
                    )

                    if (userId != null) {
                        firestore.collection("users").document(userId)
                            .set(userMap)
                            .addOnSuccessListener {
                                // Data Saved on DB
                                Toast.makeText(requireContext(), "Registration Complete!", Toast.LENGTH_SHORT).show()
                                findNavController().navigate(R.id.action_registerFragment_to_runFragment)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(requireContext(), "Error! User data Not saved: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    //register failed
                    try {
                        throw task.exception!!
                    } catch (e: FirebaseAuthUserCollisionException) {
                        // Email already registered
                        Toast.makeText(requireContext(), "Email is already Registered. Try to log-in.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Registered Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}