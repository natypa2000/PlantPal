package com.example.plantpal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.plantpal.databinding.FragmentHomeBinding
import com.example.plantpal.databinding.ItemPlantBinding
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
        setupBottomNavigation()

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

    private fun setupBottomNavigation() {
        /*binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_bookmarks -> {
                    // Navigate to Bookmarks
                    true
                }
                R.id.nav_add -> {
                    // Navigate to Add Plant
                    true
                }
                R.id.nav_calendar -> {
                    // Navigate to Calendar
                    true
                }
                R.id.nav_profile -> {
                    // Navigate to Profile
                    true
                }
                else -> false
            }
        }*/
    }

    private fun getDummyPlants(): List<Plant> {
        return listOf(
            /*Plant("ZZ Plant", "Low-light plant", "1 day", R.drawable.plant_placeholder),
            Plant("Snake Plant", "Air-purifying", "3 days", R.drawable.plant_placeholder),
            Plant("Pothos", "Easy to grow", "2 days", R.drawable.plant_placeholder)*/
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class Plant(
    val name: String,
    val description: String,
    val wateringInterval: String,
    val imageResId: Int
)

class PlantAdapter(private val plants: List<Plant>) :
    RecyclerView.Adapter<PlantAdapter.PlantViewHolder>() {

    inner class PlantViewHolder(val binding: ItemPlantBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlantViewHolder {
        val binding = ItemPlantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlantViewHolder, position: Int) {
        val plant = plants[position]
        holder.binding.apply {
            plantName.text = plant.name
            plantDescription.text = plant.description
            wateringInterval.text = plant.wateringInterval
            plantImage.setImageResource(plant.imageResId)
        }
    }

    override fun getItemCount() = plants.size
}