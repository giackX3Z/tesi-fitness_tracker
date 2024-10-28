package com.example.fitnesstracking.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.fitnesstracking.R
import com.example.fitnesstracking.databinding.FragmentSetupBinding
import com.google.firebase.auth.FirebaseAuth

class SetupFragment : Fragment(R.layout.fragment_setup) {


    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    // FirebaseAuth instance
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout using View Binding
        _binding = FragmentSetupBinding.inflate(inflater, container, false)

        // Check if user is already authenticated
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // if user already authenticated go to runFragment
            println(currentUser.uid)
            findNavController().navigate(R.id.action_setupFragment_to_runFragment)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize FirebaseAuth
        auth = FirebaseAuth.getInstance()


        // Handle login on tvContinue click
        binding.tvContinue.setOnClickListener {
            val email = binding.etMail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginUser(email, password)
            } else {
                Toast.makeText(requireContext(), "Please fill in all Fields.", Toast.LENGTH_SHORT).show()
            }
        }

        //register button handler
        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_setupFragment_to_registerFragment)
        }
    }

    // Function to log in the user
    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Login successful, navigate to the RunFragment
                    Toast.makeText(requireContext(), "Login successful!", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_setupFragment_to_runFragment)
                } else {
                    // Login failed
                    Toast.makeText(requireContext(), "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }




}