package com.example.plantpal

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.plantpal.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginUser(email, password)
            } else {
                showToast("Please enter email and password")
            }
        }

        binding.registerButton.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("LoginFragment", "Login successful for user: ${auth.currentUser?.uid}")
                    // Wait for a short time to ensure Firebase Auth state is updated
                    Handler(Looper.getMainLooper()).postDelayed({
                        checkUserAndNavigate()
                    }, 500) // 500ms delay
                } else {
                    Log.e("LoginFragment", "Login failed", task.exception)
                    showToast("Login failed: ${task.exception?.message}")
                }
            }
    }

    private fun checkUserAndNavigate() {
        val user = auth.currentUser
        if (user != null) {
            Log.d("LoginFragment", "User authenticated, navigating to home")
            findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
        } else {
            Log.e("LoginFragment", "User is null after successful login")
            showToast("Error: Unable to authenticate. Please try again.")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
