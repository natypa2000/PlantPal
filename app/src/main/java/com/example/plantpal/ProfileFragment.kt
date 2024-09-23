package com.example.plantpal

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.plantpal.databinding.FragmentProfileBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var selectedImageUri: Uri? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            binding.profileImageView.setImageURI(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        setupUI()
        loadUserData()
        setupListeners()
    }

    private fun setupUI() {
        binding.nameLayout.setEndIconOnClickListener { toggleEditMode(binding.nameEditText) }
        binding.passwordLayout.setEndIconOnClickListener { toggleEditMode(binding.passwordEditText) }
        binding.environmentNameLayout.setEndIconOnClickListener { toggleEditMode(binding.environmentNameEditText) }
    }

    private fun loadUserData() {
        val user = auth.currentUser
        user?.let { firebaseUser ->
            binding.nameEditText.setText(firebaseUser.displayName)
            binding.passwordEditText.setText("********") // Placeholder for security

            Glide.with(this)
                .load(firebaseUser.photoUrl)
                .placeholder(R.drawable.default_profile)
                .into(binding.profileImageView)

            loadEnvironmentName(firebaseUser.uid)
        }
    }

    private fun loadEnvironmentName(userId: String) {
        firestore.collection("environments")
            .whereEqualTo("creatorId", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val environmentName = documents.documents[0].getString("name")
                    binding.environmentNameEditText.setText(environmentName)
                }
            }
    }

    private fun setupListeners() {
        binding.changeProfileImageButton.setOnClickListener {
            getContent.launch("image/*")
        }

        binding.saveButton.setOnClickListener {
            updateProfile()
        }

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
        }
    }

    private fun toggleEditMode(editText: android.widget.EditText) {
        editText.isEnabled = !editText.isEnabled
        if (editText.isEnabled) {
            editText.requestFocus()
            editText.setSelection(editText.text.length)
        }
    }

    private fun updateProfile() {
        val user = auth.currentUser ?: return

        val newName = binding.nameEditText.text.toString()
        val newPassword = binding.passwordEditText.text.toString()
        val newEnvironmentName = binding.environmentNameEditText.text.toString()

        // Update display name
        if (newName != user.displayName) {
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()
            user.updateProfile(profileUpdates)
        }

        // Update password if changed
        if (newPassword != "********") {
            if (newPassword.length <5) {
                showSnackbar("Password must be at least 6 characters long")
                return
            }
            user.updatePassword(newPassword)
        }

        // Update environment name
        firestore.collection("environments")
            .whereEqualTo("creatorId", user.uid)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    documents.documents[0].reference.update("name", newEnvironmentName)
                }
            }

        // Update profile image if selected
        selectedImageUri?.let { uri ->
            val imageRef = storage.reference.child("profile_images/${user.uid}")
            imageRef.putFile(uri).addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setPhotoUri(downloadUri)
                        .build()
                    user.updateProfile(profileUpdates)
                }
            }
        }

        showSnackbar("Profile updated successfully")
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}