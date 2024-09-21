package com.example.plantpal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.plantpal.databinding.ItemPlantBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlantAdapter(
    private var plants: List<Plant>,
    private val onDeleteClick: (String) -> Unit,
    private val onEditClick: (Plant) -> Unit
) : RecyclerView.Adapter<PlantAdapter.PlantViewHolder>() {

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
            wateringInterval.text = "${plant.wateringFrequency} ${plant.frequencyPeriod}"

            // Load image using a coroutine
            CoroutineScope(Dispatchers.Main).launch {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        java.net.URL(plant.imageUrl).openStream().use {
                            android.graphics.BitmapFactory.decodeStream(it)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    plantImage.setImageBitmap(bitmap)
                } else {
                    plantImage.setImageResource(R.drawable.ic_launcher_background)
                }
            }

            // Set up delete button
            deleteButton.setOnClickListener { onDeleteClick(plant.id) }

            // Set up edit button
            editButton.setOnClickListener { onEditClick(plant) }
        }
    }

    override fun getItemCount() = plants.size

    fun updatePlants(newPlants: List<Plant>) {
        plants = newPlants
        notifyDataSetChanged()
    }
}

data class Plant(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val wateringFrequency: Int = 0,
    val frequencyPeriod: String = "",
    val imageUrl: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val creatorId: String = ""
)