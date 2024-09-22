package com.example.plantpal

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.plantpal.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var plantAdapter: PlantAdapter
    private var currentEnvironmentId: String? = null
    private var plantsListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    private suspend fun refreshTokenIfNeeded() {
        val user = auth.currentUser
        if (user != null) {
            try {
                user.getIdToken(true).await()
                Log.d("HomeFragment", "Token refreshed successfully")
            } catch (e: Exception) {
                Log.e("HomeFragment", "Error refreshing token: ${e.message}")
                throw e
            }
        } else {
            Log.e("HomeFragment", "No user logged in")
            throw IllegalStateException("No user logged in")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        setupRecyclerView()
        checkAuthenticationAndLoadData()

        binding.addEnvironmentFab.setOnClickListener {
            showCreateEnvironmentDialog()
        }

        binding.addPlantFab.setOnClickListener {
            addNewPlant()
        }
    }

    private fun checkAuthenticationAndLoadData() {
        lifecycleScope.launch {
            try {
                refreshTokenIfNeeded()
                checkForEnvironment()
            } catch (e: Exception) {
                when (e) {
                    is IllegalStateException -> {
                        showSnackbar("User not logged in. Please log in and try again.")
                        // Navigate to login screen
                    }
                    else -> showSnackbar("Error: ${e.message}")
                }
                Log.e("HomeFragment", "Error in checkAuthenticationAndLoadData", e)
            }
        }
    }

    private fun setupRecyclerView() {
        plantAdapter = PlantAdapter(
            emptyList(),
            onDeleteClick = { plant -> showDeleteConfirmationDialog(plant) },
            onEditClick = { plant -> editPlant(plant) }
        )
        binding.plantList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = plantAdapter
        }
    }

    private fun checkForEnvironment() {
        val userId = auth.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val environmentQuery = firestore.collection("environments")
                    .whereEqualTo("creatorId", userId)
                    .get()
                    .await()

                if (environmentQuery.documents.isNotEmpty()) {
                    currentEnvironmentId = environmentQuery.documents[0].id
                    withContext(Dispatchers.Main) {
                        updateUIForExistingEnvironment()
                        loadPlants()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateUIForNoEnvironment()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("Error checking for environment: ${e.message}")
                }
            }
        }
    }

    private fun updateUIForExistingEnvironment() {
        binding.addEnvironmentFab.visibility = View.GONE
        binding.addPlantFab.visibility = View.VISIBLE
        saveCurrentEnvironmentId()
    }

    private fun updateUIForNoEnvironment() {
        binding.addEnvironmentFab.visibility = View.VISIBLE
        binding.addPlantFab.visibility = View.GONE
        binding.emptyStateText.visibility = View.VISIBLE
        binding.emptyStateText.text = "Create an environment to start adding plants"
        binding.plantList.visibility = View.GONE
        saveCurrentEnvironmentId()
    }

    private fun saveCurrentEnvironmentId() {
        val sharedPref = activity?.getPreferences(android.content.Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            putString("current_environment_id", currentEnvironmentId)
            apply()
        }
    }

    private fun showCreateEnvironmentDialog() {
        val input = layoutInflater.inflate(R.layout.dialog_create_environment, null)
        val editText = input.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.environmentNameInput)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create Environment")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val environmentName = editText.text.toString()
                if (environmentName.isNotEmpty()) {
                    createEnvironment(environmentName)
                } else {
                    showSnackbar("Please enter an environment name")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createEnvironment(name: String) {
        val userId = auth.currentUser?.uid ?: return
        val environment = hashMapOf(
            "name" to name,
            "creatorId" to userId,
            "users" to mapOf(userId to "creator")
        )

        lifecycleScope.launch {
            try {
                refreshTokenIfNeeded()
                val documentRef = firestore.collection("environments").add(environment).await()
                currentEnvironmentId = documentRef.id
                withContext(Dispatchers.Main) {
                    updateUIForExistingEnvironment()
                    showSnackbar("Environment created successfully")
                    loadPlants()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("Error creating environment: ${e.message}")
                }
            }
        }
    }

    private fun loadPlants() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            showSnackbar("User not authenticated")
            return
        }

        currentEnvironmentId?.let { envId ->
            plantsListener?.remove()
            plantsListener = firestore.collection("plants")
                .whereEqualTo("environmentId", envId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        showSnackbar("Error loading plants: ${e.message}")
                        return@addSnapshotListener
                    }
                    val plants = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(Plant::class.java)?.apply { id = doc.id }
                    } ?: emptyList()
                    plantAdapter.updatePlants(plants)
                    updateEmptyState(plants.isEmpty())
                }
        } ?: run {
            showSnackbar("No environment selected")
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        _binding?.let { binding ->
            if (isEmpty) {
                binding.emptyStateText.visibility = View.VISIBLE
                binding.emptyStateText.text = "No plants added yet. Click the + button to add a plant."
                binding.plantList.visibility = View.GONE
            } else {
                binding.emptyStateText.visibility = View.GONE
                binding.plantList.visibility = View.VISIBLE
            }
        }
    }

    private fun showDeleteConfirmationDialog(plant: Plant) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Plant")
            .setMessage("Are you sure you want to delete ${plant.name}?")
            .setPositiveButton("Delete") { _, _ -> deletePlant(plant) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePlant(plant: Plant) {
        if (plant.id.isBlank()) {
            showSnackbar("Error: Invalid plant ID")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                firestore.collection("plants").document(plant.id).delete().await()

                if (plant.imageUrl.isNotEmpty()) {
                    try {
                        storage.getReferenceFromUrl(plant.imageUrl).delete().await()
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "Error deleting plant image: ${e.message}")
                        // Continue with the deletion process even if image deletion fails
                    }
                }

                withContext(Dispatchers.Main) {
                    showSnackbar("Plant deleted successfully")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("Error deleting plant: ${e.message}")
                    Log.e("HomeFragment", "Error deleting plant", e)
                }
            }
        }
    }

    private fun editPlant(plant: Plant) {
        val action = HomeFragmentDirections.actionHomeFragmentToAddPlantFragment(
            plantId = plant.id,
            environmentId = plant.environmentId
        )
        findNavController().navigate(action)
    }

    private fun addNewPlant() {
        currentEnvironmentId?.let { envId ->
            val action = HomeFragmentDirections.actionHomeFragmentToAddPlantFragment(
                plantId = null,
                environmentId = envId
            )
            findNavController().navigate(action)
        } ?: showSnackbar("Please create an environment first")
    }

    private fun showSnackbar(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        plantsListener?.remove()
        _binding = null
    }
}