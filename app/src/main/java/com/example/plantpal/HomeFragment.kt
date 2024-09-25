package com.example.plantpal

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import android.widget.AdapterView



class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var plantAdapter: PlantAdapter
    private var currentEnvironmentId: String? = null
    private var plantsListener: ListenerRegistration? = null
    private var environments: List<Environment> = listOf()
    private lateinit var environmentAdapter: ArrayAdapter<String>

    data class Environment(val id: String, val name: String)

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
        setupEnvironmentSpinner()
        loadEnvironments()
    }
    private fun setupEnvironmentSpinner() {
        environmentAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, mutableListOf())
        environmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.environmentSpinner.adapter = environmentAdapter

        binding.environmentSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val selectedEnvironment = environments[position]
                currentEnvironmentId = selectedEnvironment.id  // Update currentEnvironmentId when selection changes
                loadPlants(selectedEnvironment.id)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    // Add this new method

    private fun loadEnvironments() {
        Log.d("HomeFragment", "Loading environments")
        val currentUserId = auth.currentUser?.uid ?: return

        firestore.collection("environments")
            .get()
            .addOnSuccessListener { allDocs ->
                Log.d("HomeFragment", "Total environments: ${allDocs.size()}")
                val userEnvironments = allDocs.mapNotNull { doc ->
                    val creatorId = doc.getString("creatorId")
                    val users = doc.get("users") as? Map<String, Any>
                    when {
                        creatorId == currentUserId -> {
                            Log.d("HomeFragment", "Found creator environment: ${doc.id}")
                            Environment(doc.id, doc.getString("name") ?: "Unnamed")
                        }
                        users?.containsKey(currentUserId) == true -> {
                            Log.d("HomeFragment", "Found viewer environment: ${doc.id}")
                            Environment(doc.id, doc.getString("name") ?: "Unnamed")
                        }
                        else -> null
                    }
                }

                environments = userEnvironments.distinctBy { it.id }
                Log.d("HomeFragment", "User's environments: ${environments.size}")
                updateEnvironmentSpinner()

                if (environments.isNotEmpty()) {
                    updateUIForExistingEnvironment()
                    currentEnvironmentId = environments.first().id  // Set currentEnvironmentId to the first environment
                    loadPlants(currentEnvironmentId!!)
                } else {
                    updateUIForNoEnvironment()
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "Error loading environments", e)
                showSnackbar("Error loading environments: ${e.message}")
            }
    }

    // Add this new method
    private fun updateEnvironmentSpinner() {
        Log.d("HomeFragment", "Updating environment spinner")
        environmentAdapter.clear()
        environmentAdapter.addAll(environments.map { it.name })
        environmentAdapter.notifyDataSetChanged()

        if (environments.isNotEmpty()) {
            binding.environmentSpinner.setSelection(0)
            currentEnvironmentId = environments.first().id
            Log.d("HomeFragment", "Set current environment ID to: $currentEnvironmentId")
            saveCurrentEnvironmentId()
        }
    }

    private fun checkAuthenticationAndLoadData() {
        Log.d("HomeFragment", "Checking authentication and loading data")
        auth.currentUser?.let { user ->
            Log.d("HomeFragment", "User authenticated: ${user.uid}")
            loadEnvironments()
        } ?: run {
            Log.d("HomeFragment", "User not authenticated")
            updateUIForNoEnvironment()
            showSnackbar("User not authenticated")
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

                withContext(Dispatchers.Main) {
                    if (environmentQuery.documents.isNotEmpty()) {
                        loadEnvironments()
                    } else {
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
        Log.d("HomeFragment", "Updating UI for existing environment")
        binding.addEnvironmentFab.visibility = View.GONE
        binding.addPlantFab.visibility = View.VISIBLE
        binding.environmentSpinner.visibility = View.VISIBLE
        binding.emptyStateText.visibility = View.GONE
        binding.plantList.visibility = View.VISIBLE
    }

    private fun updateUIForNoEnvironment() {
        Log.d("HomeFragment", "Updating UI for no environment")
        binding.addEnvironmentFab.visibility = View.VISIBLE
        binding.addPlantFab.visibility = View.GONE
        binding.environmentSpinner.visibility = View.GONE
        binding.emptyStateText.visibility = View.VISIBLE
        binding.emptyStateText.text = "Create an environment to start adding plants"
        binding.plantList.visibility = View.GONE
    }

    private fun saveCurrentEnvironmentId() {
        val sharedPref = activity?.getPreferences(android.content.Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            putString("current_environment_id", currentEnvironmentId)
            apply()
        }
        Log.d("HomeFragment", "Saved current environment ID to SharedPreferences: $currentEnvironmentId")
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
                withContext(Dispatchers.Main) {
                    showSnackbar("Environment created successfully")
                    loadEnvironments()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("Error creating environment: ${e.message}")
                }
            }
        }
    }

    private fun loadPlants(environmentId: String) {
        Log.d("HomeFragment", "Loading plants for environment: $environmentId")
        currentEnvironmentId = environmentId

        plantsListener?.remove()
        plantsListener = firestore.collection("plants")
            .whereEqualTo("environmentId", environmentId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("HomeFragment", "Error loading plants", e)
                    showSnackbar("Error loading plants: ${e.message}")
                    return@addSnapshotListener
                }
                val plants = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Plant::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                Log.d("HomeFragment", "Loaded ${plants.size} plants")
                plantAdapter.updatePlants(plants)
                updateEmptyState(plants.isEmpty())
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
            Log.d("HomeFragment", "Adding new plant. Current environment ID: $envId")
            val action = HomeFragmentDirections.actionHomeFragmentToAddPlantFragment(
                plantId = null,
                environmentId = envId
            )
            findNavController().navigate(action)
        } ?: run {
            Log.e("HomeFragment", "Attempted to add plant without a selected environment")
            showSnackbar("Please select an environment first")
        }
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