package com.example.plantpal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.plantpal.databinding.ItemPlantBinding

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

data class Plant(
    val name: String,
    val description: String,
    val wateringInterval: String,
    val imageResId: Int
)