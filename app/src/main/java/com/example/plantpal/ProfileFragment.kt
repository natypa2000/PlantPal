package com.example.plantpal

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.plantpal.databinding.FragmentProfileBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

data class PermissionRequest(
    val id: String = "",
    val userId: String = "",
    val environmentId: String = "",
    val requestedRole: String = "",
    val status: String = "pending"
)


class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var viewersAdapter: ViewersAdapter
    private lateinit var permissionRequestsAdapter: PermissionRequestsAdapter
    private var selectedImageUri: Uri? = null
    private var currentEnvironmentId: String? = null


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

        setupRecyclerView()
        setupUI()
        checkUserAuthentication()
    }
    private fun setupRecyclerView() {
        viewersAdapter = ViewersAdapter(
            onDeleteClick = { userId -> removeUser(userId) },
            onRoleChange = { userId, newRole -> changeUserRole(userId, newRole) }
        )
        binding.viewersRecyclerView.apply {
            adapter = viewersAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun checkUserAuthentication() {
        val user = auth.currentUser
        if (user != null) {
            Log.d("ProfileFragment", "User authenticated: ${user.uid}")
            setupUI()
            loadUserData()
        } else {
            Log.e("ProfileFragment", "User not authenticated, navigating to login")
            findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
        }
    }


    private fun setupUI() {
        binding.addViewerButton.setOnClickListener {
            showAddViewerDialog()
        }

        binding.saveButton.setOnClickListener {
            updateProfile()
        }

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
        }

        binding.nameLayout.setEndIconOnClickListener { toggleEditMode(binding.nameEditText) }
        binding.passwordLayout.setEndIconOnClickListener { toggleEditMode(binding.passwordEditText) }
        binding.environmentNameLayout.setEndIconOnClickListener { toggleEditMode(binding.environmentNameEditText) }

        binding.changeProfileImageButton.setOnClickListener {
            getContent.launch("image/*")
        }
    }

    private fun setupRecyclerViews() {
        viewersAdapter = ViewersAdapter(
            onDeleteClick = { userId -> removeUser(userId) },
            onRoleChange = { userId, newRole -> changeUserRole(userId, newRole) }
        )
        binding.viewersRecyclerView.adapter = viewersAdapter
        binding.viewersRecyclerView.layoutManager = LinearLayoutManager(context)

        permissionRequestsAdapter = PermissionRequestsAdapter(
            onAccept = { requestId -> acceptPermissionRequest(requestId) },
            onReject = { requestId -> rejectPermissionRequest(requestId) }
        )
        binding.permissionRequestsRecyclerView.adapter = permissionRequestsAdapter
        binding.permissionRequestsRecyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun loadUserData() {
        val user = auth.currentUser ?: return
        user?.let { firebaseUser ->
            binding.nameEditText.setText(firebaseUser.displayName)
            binding.passwordEditText.setText("********") // Placeholder for security

            Glide.with(this)
                .load(firebaseUser.photoUrl)
                .placeholder(R.drawable.default_profile)
                .into(binding.profileImageView)

            loadEnvironmentData(firebaseUser.uid)
        }
        loadEnvironmentData(user.uid)
    }

    private fun loadEnvironmentData(userId: String) {
        firestore.collection("environments")
            .whereEqualTo("creatorId", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val environment = documents.documents[0]
                    currentEnvironmentId = environment.id
                    binding.environmentNameEditText.setText(environment.getString("name"))
                    loadUsers(environment.id)
                }
            }
    }

    private fun loadUsers(environmentId: String) {
        firestore.collection("environments").document(environmentId)
            .get()
            .addOnSuccessListener { document ->
                val users = document.get("users") as? Map<String, String>
                val creatorId = document.getString("creatorId")

                if (users != null && creatorId != null) {
                    val usersList = mutableListOf<User>()
                    var loadedUsers = 0

                    for ((uid, role) in users) {
                        if (uid != creatorId) {
                            firestore.collection("users").document(uid)
                                .get()
                                .addOnSuccessListener { userDoc ->
                                    val username = userDoc.getString("username") ?: uid
                                    usersList.add(User(uid, username, role))
                                    loadedUsers++

                                    if (loadedUsers == users.size - 1) {  // -1 to account for creator
                                        viewersAdapter.submitList(usersList)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    showSnackbar("Error loading user data: ${e.message}")
                                }
                        }
                    }
                } else {
                    viewersAdapter.submitList(emptyList())
                }
            }
            .addOnFailureListener { e ->
                showSnackbar("Error loading users: ${e.message}")
            }
    }

    private fun loadPermissionRequests(environmentId: String) {
        firestore.collection("permissionRequests")
            .whereEqualTo("environmentId", environmentId)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                val requests = documents.mapNotNull { it.toObject(PermissionRequest::class.java) }
                permissionRequestsAdapter.submitList(requests)
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

        binding.addViewerButton.setOnClickListener {
            showAddViewerDialog()
        }
    }

    private fun toggleEditMode(editText: EditText) {
        editText.isEnabled = !editText.isEnabled
        if (editText.isEnabled) {
            editText.requestFocus()
            editText.setSelection(editText.text.length)
        }
    }

    private fun updateProfile() {
        val user = auth.currentUser ?: return

        val newName = binding.nameEditText.text.toString().trim()
        val newPassword = binding.passwordEditText.text.toString()
        val newEnvironmentName = binding.environmentNameEditText.text.toString().trim()

        Log.d("ProfileFragment", "Current display name: ${user.displayName}, New name: $newName")
        if (newName != user.displayName) {
            Log.d("ProfileFragment", "Username has changed, updating...")
            updateUsername(user.uid, newName)
        } else {
            Log.d("ProfileFragment", "Username hasn't changed, skipping update")
        }

        if (newName != user.displayName) {
            updateUsername(user.uid, newName)
        }

        if (newPassword != "********") {
            if (newPassword.length < 6) {
                showSnackbar("Password must be at least 6 characters long")
                return
            }
            user.updatePassword(newPassword)
                .addOnSuccessListener {
                    showSnackbar("Password updated successfully")
                }
                .addOnFailureListener { e ->
                    showSnackbar("Failed to update password: ${e.message}")
                }
        }

        currentEnvironmentId?.let { envId ->
            firestore.collection("environments").document(envId)
                .update("name", newEnvironmentName)
                .addOnSuccessListener {
                    showSnackbar("Environment name updated successfully")
                }
                .addOnFailureListener { e ->
                    showSnackbar("Failed to update environment name: ${e.message}")
                }
        }

        selectedImageUri?.let { uri ->
            val imageRef = storage.reference.child("profile_images/${user.uid}")
            imageRef.putFile(uri).addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setPhotoUri(downloadUri)
                        .build()
                    user.updateProfile(profileUpdates)
                        .addOnSuccessListener {
                            showSnackbar("Profile picture updated successfully")
                        }
                        .addOnFailureListener { e ->
                            showSnackbar("Failed to update profile picture: ${e.message}")
                        }
                }
            }
        }
    }

    private fun updateUsername(userId: String, newUsername: String) {
        Log.d("ProfileFragment", "Attempting to update username to: $newUsername")
        firestore.collection("usernames").document(newUsername.lowercase())
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    showSnackbar("Username is already taken")
                } else {
                    updateUsernameInFirestore(userId, newUsername)
                }
            }
            .addOnFailureListener { e ->
                showSnackbar("Error checking username availability: ${e.message}")
            }
    }

    private fun updateUsernameInFirestore(userId: String, newUsername: String) {
        val batch = firestore.batch()

        // Update in 'users' collection
        val userRef = firestore.collection("users").document(userId)
        batch.update(userRef, "username", newUsername)

        // Find and delete old username document
        firestore.collection("usernames")
            .whereEqualTo("uid", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    for (document in documents) {
                        batch.delete(document.reference)
                    }
                }

                // Create new username document
                val newUsernameRef = firestore.collection("usernames").document(newUsername.lowercase())
                batch.set(newUsernameRef, hashMapOf(
                    "uid" to userId,
                    "email" to (auth.currentUser?.email ?: "")
                ))

                // Commit the batch
                batch.commit().addOnSuccessListener {
                    // Update in Authentication
                    val user = auth.currentUser
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(newUsername)
                        .build()
                    user?.updateProfile(profileUpdates)
                        ?.addOnSuccessListener {
                            showSnackbar("Username updated successfully")
                        }
                        ?.addOnFailureListener { e ->
                            showSnackbar("Failed to update username in Authentication: ${e.message}")
                        }
                }.addOnFailureListener { e ->
                    showSnackbar("Failed to update username: ${e.message}")
                }
            }
            .addOnFailureListener { e ->
                showSnackbar("Error updating username: ${e.message}")
            }
    }

    private fun showAddViewerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_viewer, null)
        val usernameEditText = dialogView.findViewById<EditText>(R.id.usernameEditText)
        val roleSpinner = dialogView.findViewById<Spinner>(R.id.roleSpinner)

        val roles = arrayOf("viewer", "editor")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roleSpinner.adapter = adapter

        AlertDialog.Builder(requireContext())
            .setTitle("Add User")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val username = usernameEditText.text.toString()
                val role = roleSpinner.selectedItem.toString()
                addViewer(username, role)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addViewer(username: String, role: String) {
        if (username.isBlank()) {
            showSnackbar("Please enter a valid username")
            return
        }

        firestore.collection("usernames")
            .document(username.lowercase())
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val uid = document.getString("uid")
                    if (uid != null) {
                        addUserToEnvironment(uid, role)
                    } else {
                        showSnackbar("Error: User ID not found")
                    }
                } else {
                    showSnackbar("Username not found")
                }
            }
            .addOnFailureListener { e ->
                showSnackbar("Error looking up username: ${e.message}")
            }
    }

    private fun addUserToEnvironment(uid: String, role: String) {
        currentEnvironmentId?.let { envId ->
            firestore.collection("environments").document(envId)
                .get()
                .addOnSuccessListener { environmentDoc ->
                    val users = environmentDoc.get("users") as? MutableMap<String, String> ?: mutableMapOf()
                    val creatorId = environmentDoc.getString("creatorId")

                    if (uid == creatorId) {
                        showSnackbar("Cannot add creator as viewer")
                        return@addOnSuccessListener
                    }

                    if (users.size >= 5) {
                        showSnackbar("Maximum number of users (5) reached")
                        return@addOnSuccessListener
                    }

                    users[uid] = role
                    environmentDoc.reference.update("users", users)
                        .addOnSuccessListener {
                            showSnackbar("User added successfully")
                            loadUsers(envId)
                        }
                        .addOnFailureListener { e ->
                            showSnackbar("Failed to add user: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    showSnackbar("Error accessing environment data")
                }
        } ?: run {
            showSnackbar("Error: No active environment")
        }
    }
    private fun removeUser(userId: String) {
        currentEnvironmentId?.let { envId ->
            firestore.collection("environments").document(envId)
                .get()
                .addOnSuccessListener { document ->
                    val users = document.get("users") as? MutableMap<String, String> ?: return@addOnSuccessListener
                    users.remove(userId)
                    document.reference.update("users", users)
                        .addOnSuccessListener {
                            showSnackbar("User removed successfully")
                            loadUsers(envId)
                        }
                        .addOnFailureListener {
                            showSnackbar("Failed to remove user")
                        }
                }
        }
    }

    private fun changeUserRole(userId: String, newRole: String) {
        currentEnvironmentId?.let { envId ->
            firestore.collection("environments").document(envId)
                .get()
                .addOnSuccessListener { document ->
                    val users = document.get("users") as? MutableMap<String, String> ?: return@addOnSuccessListener
                    users[userId] = newRole
                    document.reference.update("users", users)
                        .addOnSuccessListener {
                            showSnackbar("User role updated successfully")
                            loadUsers(envId)
                        }
                        .addOnFailureListener {
                            showSnackbar("Failed to update user role")
                        }
                }
        }
    }

    private fun acceptPermissionRequest(requestId: String) {
        firestore.collection("permissionRequests").document(requestId)
            .get()
            .addOnSuccessListener { document ->
                val request = document.toObject(PermissionRequest::class.java) ?: return@addOnSuccessListener
                addViewer(request.userId, request.requestedRole)
                document.reference.update("status", "accepted")
                    .addOnSuccessListener {
                        showSnackbar("Permission request accepted")
                        loadPermissionRequests(request.environmentId)
                    }
            }
    }

    private fun rejectPermissionRequest(requestId: String) {
        firestore.collection("permissionRequests").document(requestId)
            .update("status", "rejected")
            .addOnSuccessListener {
                showSnackbar("Permission request rejected")
                currentEnvironmentId?.let { envId ->
                    loadPermissionRequests(envId)
                }
            }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}