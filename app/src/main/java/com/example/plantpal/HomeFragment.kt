package com.example.plantpal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.plantpal.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        val user = auth.currentUser
        binding.welcomeText.text = "Welcome, ${user?.email ?: "User"}!"

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            // Navigate back to LoginFragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LoginFragment())
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}