package com.example.plantpal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.plantpal.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        setupRecyclerView()
        loadPlants()

        binding.searchIcon.setOnClickListener {
            Toast.makeText(context, "Search functionality coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.addPlantFab.setOnClickListener {
            addNewPlant()
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

    private fun loadPlants() {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection("plants")
            .whereEqualTo("creatorId", currentUserId)
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
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        _binding?.let { binding ->
            binding.emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.plantList.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }

    private fun showDeleteConfirmationDialog(plant: Plant) {
        if (plant.id.isBlank()) {
            showSnackbar("Error: Invalid plant ID")
            return
        }
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
        showLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Delete image from Storage
                if (plant.imageUrl.isNotBlank()) {
                    val imageRef = storage.getReferenceFromUrl(plant.imageUrl)
                    imageRef.delete().await()
                }

                // Delete plant document from Firestore
                firestore.collection("plants").document(plant.id).delete().await()

                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showSnackbar("${plant.name} deleted successfully")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showSnackbar("Error deleting ${plant.name}: ${e.message}")
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        _binding?.let { binding ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.addPlantFab.isEnabled = !isLoading
        }
    }

    private fun showSnackbar(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() }
    }

    private fun editPlant(plant: Plant) {
        val action = HomeFragmentDirections.actionHomeFragmentToAddPlantFragment(plant.id)
        findNavController().navigate(action)
    }

    private fun addNewPlant() {
        val action = HomeFragmentDirections.actionHomeFragmentToAddPlantFragment(null)
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}