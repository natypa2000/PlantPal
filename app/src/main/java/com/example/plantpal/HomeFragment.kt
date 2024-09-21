package com.example.plantpal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.plantpal.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
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

        setupRecyclerView()
        loadPlants()

        binding.searchIcon.setOnClickListener {
            // Implement search functionality
        }

        binding.addPlantFab.setOnClickListener {
            addNewPlant()
        }
    }

    private fun setupRecyclerView() {
        plantAdapter = PlantAdapter(
            emptyList(),
            onDeleteClick = { plantId -> deletePlant(plantId) },
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
                    // Handle error
                    return@addSnapshotListener
                }
                val plants = snapshot?.toObjects(Plant::class.java) ?: emptyList()
                plantAdapter.updatePlants(plants)
            }
    }

    private fun deletePlant(plantId: String) {
        firestore.collection("plants").document(plantId)
            .delete()
            .addOnSuccessListener {
                // Plant deleted successfully
                // You might want to show a success message to the user
            }
            .addOnFailureListener { e ->
                // Handle the error
                // You might want to show an error message to the user
            }
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