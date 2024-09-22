package com.example.plantpal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.plantpal.databinding.FragmentBookmarksBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class BookmarksFragment : Fragment() {

    private var _binding: FragmentBookmarksBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var plantAdapter: PlantAdapter
    private var allPlants: List<Plant> = emptyList()
    private var firestoreListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBookmarksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firestore = FirebaseFirestore.getInstance()
        setupRecyclerView()
        setupTagFilter()
        loadPlants()
    }

    private fun setupRecyclerView() {
        plantAdapter = PlantAdapter(emptyList(), {}, {})
        binding.recyclerViewBookmarkedPlants.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = plantAdapter
        }
    }

    private fun setupTagFilter() {
        binding.spinnerTagFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedTag = parent?.getItemAtPosition(position) as? String
                filterPlantsByTag(selectedTag)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadPlants() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            // Handle unauthenticated user
            return
        }

        firestoreListener = firestore.collection("plants")
            .whereEqualTo("creatorId", currentUserId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    // Handle error
                    return@addSnapshotListener
                }
                allPlants = snapshot?.toObjects(Plant::class.java) ?: emptyList()
                updateTagSpinner()
                plantAdapter.updatePlants(allPlants)
                updateEmptyState()
            }
    }

    private fun updateTagSpinner() {
        val tags = allPlants.flatMap { it.tags }.distinct().sorted()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, tags)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        _binding?.spinnerTagFilter?.adapter = adapter
    }

    private fun filterPlantsByTag(tag: String?) {
        val filteredPlants = if (tag == null) {
            allPlants
        } else {
            allPlants.filter { it.tags.contains(tag) }
        }
        plantAdapter.updatePlants(filteredPlants)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        _binding?.let { binding ->
            if (allPlants.isEmpty()) {
                binding.textViewNoBookmarks.visibility = View.VISIBLE
                binding.recyclerViewBookmarkedPlants.visibility = View.GONE
            } else {
                binding.textViewNoBookmarks.visibility = View.GONE
                binding.recyclerViewBookmarkedPlants.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firestoreListener?.remove()
        _binding = null
    }
}