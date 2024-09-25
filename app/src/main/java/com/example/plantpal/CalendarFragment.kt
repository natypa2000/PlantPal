package com.example.plantpal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.plantpal.databinding.FragmentCalendarBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: FirebaseDatabase

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = FirebaseDatabase.getInstance()
        val eventsRef = database.getReference("events")
        setUsername()
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(year, month, dayOfMonth)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formattedDate = dateFormat.format(selectedDate.time)

            binding.editTextEvent.setText("")
            binding.buttonAddEvent.setOnClickListener {
                val eventText = binding.editTextEvent.text.toString()
                if (eventText.isNotEmpty()) {
                    addEvent(formattedDate, eventText)
                }
            }

            loadEvents(formattedDate)
        }
    }

    private fun addEvent(date: String, event: String) {
        val eventsRef = database.getReference("events").child(date)
        val eventId = eventsRef.push().key
        eventId?.let {
            eventsRef.child(it).setValue(event)
                .addOnSuccessListener {
                    Toast.makeText(context, "Event added successfully", Toast.LENGTH_SHORT).show()
                    binding.editTextEvent.setText("")
                    loadEvents(date)
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to add event", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setUsername() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            val displayName = user.displayName ?: "User"
            view?.findViewById<TextView>(R.id.textViewUsername)?.text = "Hello $displayName"
        }
    }

    private fun loadEvents(date: String) {
        val eventsRef = database.getReference("events").child(date)
        eventsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val events = mutableListOf<String>()
                for (childSnapshot in snapshot.children) {
                    val event = childSnapshot.getValue(String::class.java)
                    event?.let { events.add(it) }
                }
                updateEventsList(events)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load events", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateEventsList(events: List<String>) {
        val eventsText = events.joinToString("\n")
        binding.textViewEvents.text = if (eventsText.isNotEmpty()) eventsText else "No events"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}