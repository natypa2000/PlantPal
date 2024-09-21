package com.example.plantpal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.plantpal.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        setupRecyclerView()
        checkForEnvironment()

        binding.addEnvironmentFab.setOnClickListener {
            showCreateEnvironmentDialog()
        }

        binding.addPlantFab.setOnClickListener {
            if (currentEnvironmentId != null) {
                addNewPlant()
            } else {
                showSnackbar("Please create an environment first")
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
                        binding.addEnvironmentFab.visibility = View.GONE
                        binding.addPlantFab.visibility = View.VISIBLE
                        loadPlants()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.addEnvironmentFab.visibility = View.VISIBLE
                        binding.addPlantFab.visibility = View.GONE
                        showSnackbar("Create an environment to start adding plants")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("Error checking for environment: ${e.message}")
                }
            }
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
            "users" to mapOf(userId to "creator")  // This ensures the creator has full permissions
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val documentRef = firestore.collection("environments").add(environment).await()
                currentEnvironmentId = documentRef.id
                withContext(Dispatchers.Main) {
                    binding.addEnvironmentFab.visibility = View.GONE
                    binding.addPlantFab.visibility = View.VISIBLE
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
        currentEnvironmentId?.let { envId ->
            firestore.collection("plants")
                .whereEqualTo("environmentId", envId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        showSnackbar("Error loading plants: ${e.message}")
                        return@addSnapshotListener
                    }
                    val plants = snapshot?.toObjects(Plant::class.java) ?: emptyList()
                    plantAdapter.updatePlants(plants)
                    updateEmptyState(plants.isEmpty())
                }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.plantList.visibility = if (isEmpty) View.GONE else View.VISIBLE
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                firestore.collection("plants").document(plant.id).delete().await()
                if (plant.imageUrl.isNotEmpty()) {
                    storage.getReferenceFromUrl(plant.imageUrl).delete().await()
                }
                withContext(Dispatchers.Main) {
                    showSnackbar("Plant deleted successfully")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("Error deleting plant: ${e.message}")
                }
            }
        }
    }

    private fun editPlant(plant: Plant) {
        currentEnvironmentId?.let { envId ->
            val action = HomeFragmentDirections.actionHomeFragmentToAddPlantFragment(plant.id, envId)
            findNavController().navigate(action)
        }
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
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}