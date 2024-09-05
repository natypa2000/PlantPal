package com.example.plantpal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.plantpal.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
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

        setupRecyclerView()

        binding.searchIcon.setOnClickListener {
            // Implement search functionality
        }
    }

    private fun setupRecyclerView() {
        plantAdapter = PlantAdapter(getDummyPlants())
        binding.plantList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = plantAdapter
        }
    }

    private fun getDummyPlants(): List<Plant> {
        return listOf(
            Plant("ZZ Plant", "Low-light plant", "1 day", R.drawable.ic_launcher_background),
            Plant("Snake Plant", "Air-purifying", "3 days", R.drawable.ic_launcher_background),
            Plant("Pothos", "Easy to grow", "2 days", R.drawable.ic_launcher_background)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}