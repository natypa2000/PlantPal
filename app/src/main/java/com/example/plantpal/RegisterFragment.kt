package com.example.plantpal

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.plantpal.databinding.FragmentRegisterBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var loadingSpinner: LoadingSpinner


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        loadingSpinner = LoadingSpinner(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        binding.registerButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val username = binding.usernameEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString()
            val confirmPassword = binding.confirmPasswordEditText.text.toString()

            if (validateInput(email, username, password, confirmPassword)) {
                registerUser(email, username, password)
            }
        }
        binding.backToLoginButton.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }
    }

    private fun validateInput(email: String, username: String, password: String, confirmPassword: String): Boolean {
        if (email.isEmpty() || username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.length < 6) {
            Toast.makeText(context, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password != confirmPassword) {
            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun registerUser(email: String, username: String, password: String) {
        loadingSpinner.show()
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    loadingSpinner.dismiss()
                    val user = auth.currentUser
                    if (user != null) {
                        val uid = user.uid

                        // Create user document
                        val userDoc = hashMapOf(
                            "username" to username,
                            "email" to email
                        )
                        firestore.collection("users").document(uid).set(userDoc)

                        // Create username document
                        val usernameDoc = hashMapOf(
                            "uid" to uid,
                            "email" to email
                        )
                        firestore.collection("usernames").document(username.lowercase()).set(usernameDoc)

                        // Update display name in Firebase Auth
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(username)
                            .build()
                        user.updateProfile(profileUpdates)

                        showSuccessMessageAndRedirect()
                    }
                } else {
                    showSnackbar("Registration failed: ${task.exception?.message}")
                }
            }
    }

    private fun saveUsernameEmailMapping(username: String, email: String) {
        Log.d("RegisterFragment", "Saving username-email mapping: $username - $email")
        val usernameDoc = hashMapOf(
            "email" to email,
            "username" to username
        )

        firestore.collection("usernames")
            .document(username.lowercase())
            .set(usernameDoc)
            .addOnSuccessListener {
                Log.d("RegisterFragment", "Username-email mapping saved successfully")
                showSuccessMessageAndRedirect()
            }
            .addOnFailureListener { e ->
                Log.e("RegisterFragment", "Error saving username-email mapping", e)
                showSnackbar("Error saving username: ${e.message}")
            }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
    }
    private fun showSuccessMessageAndRedirect() {
        Toast.makeText(context, "Registration successful", Toast.LENGTH_SHORT).show()
        // Sign out the user since we want them to log in manually
        auth.signOut()
        // Delay the navigation to allow the Toast to be visible
        view?.postDelayed({
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }, 2000) // 2000 milliseconds = 2 seconds
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}