package com.example.plantpal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.plantpal.databinding.ItemPlantBinding
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlantAdapter(
    private var plants: List<Plant>,
    private val onDeleteClick: (Plant) -> Unit,
    private val onEditClick: (Plant) -> Unit,
    private val onPlantClick: (Plant) -> Unit
) : RecyclerView.Adapter<PlantAdapter.PlantViewHolder>() {

    inner class PlantViewHolder(val binding: ItemPlantBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPlantClick(plants[position])
                }
            }
        }
    }

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

            deleteButton.setOnClickListener { onDeleteClick(plant) }
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
    var id: String = "",
    val name: String = "",
    val description: String = "",
    val wateringFrequency: Int = 0,
    val frequencyPeriod: String = "",
    val imageUrl: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val creatorId: String = "",
    val environmentId: String = ""
) {
    constructor(doc: DocumentSnapshot) : this(
        id = doc.id,
        name = doc.getString("name") ?: "",
        description = doc.getString("description") ?: "",
        wateringFrequency = doc.getLong("wateringFrequency")?.toInt() ?: 0,
        frequencyPeriod = doc.getString("frequencyPeriod") ?: "",
        imageUrl = doc.getString("imageUrl") ?: "",
        notes = doc.getString("notes") ?: "",
        tags = doc.get("tags") as? List<String> ?: emptyList(),
        creatorId = doc.getString("creatorId") ?: "",
        environmentId = doc.getString("environmentId") ?: ""
    )
}