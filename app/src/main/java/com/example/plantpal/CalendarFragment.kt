package com.example.plantpal

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.plantpal.databinding.FragmentCalendarBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private val database = Firebase.database.reference
    private val TAG = "CalendarFragment"
    private lateinit var currentDate: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?

    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUsername()
        Log.d(TAG, "onViewCreated called")

        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
            }
            currentDate = selectedDate.time.formatToString()
            Log.d(TAG, "Date selected: $currentDate")

            binding.editTextEvent.setText("")
            loadEvents(currentDate)
        }


        binding.buttonAddEvent.setOnClickListener {
            Log.d(TAG, "Add Event button clicked")
            val eventText = binding.editTextEvent.text.toString()
            if (eventText.isNotEmpty()) {
                addEvent(currentDate, eventText)
            } else {
                Toast.makeText(context, "Please enter an event", Toast.LENGTH_SHORT).show()
            }
        }

        // Set initial date
        currentDate = Calendar.getInstance().time.formatToString()
        loadEvents(currentDate)
    }

    private fun addEvent(date: String, event: String) {
        Log.d(TAG, "Attempting to add event: $event for date: $date")

        // Add to local storage
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        val events = sharedPref.getStringSet("$date-events", mutableSetOf()) ?: mutableSetOf()
        events.add(event)
        with (sharedPref.edit()) {
            putStringSet("$date-events", events)
            apply()
        }
        Log.d(TAG, "Event added to local storage")

        // Add to Firebase
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val eventsRef = database.child("events").child(date)
                val eventId = eventsRef.push().key ?: return@launch
                eventsRef.child(eventId).setValue(event).await()
                Log.d(TAG, "Event added to Firebase successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding event to Firebase", e)
            }
        }

        Toast.makeText(context, "Event added", Toast.LENGTH_SHORT).show()
        binding.editTextEvent.setText("")
        loadEvents(date)
    }

    private fun setUsername() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            val displayName = user.displayName ?: "User"
            view?.findViewById<TextView>(R.id.textViewUsername)?.text = "Hello $displayName"
        }
    }

    private fun loadEvents(date: String) {
        Log.d(TAG, "Loading events for date: $date")

        // Load from local storage
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        val localEvents = sharedPref.getStringSet("$date-events", mutableSetOf()) ?: mutableSetOf()
        Log.d(TAG, "Local events: $localEvents")

        // Update UI with local events
        updateEventsList(localEvents.toList())

        // Load from Firebase
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val eventsRef = database.child("events").child(date)
                val snapshot = eventsRef.get().await()
                val firebaseEvents = snapshot.children.mapNotNull { it.getValue<String>() }
                Log.d(TAG, "Firebase events: $firebaseEvents")

                // Combine local and Firebase events
                val allEvents = (localEvents + firebaseEvents).toSet().toList()
                updateEventsList(allEvents)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading events from Firebase", e)
            }
        }
    }

    private fun updateEventsList(events: List<String>) {
        val eventsText = events.joinToString("\n")
        binding.textViewEvents.text = if (eventsText.isNotEmpty()) eventsText else "No events"
        Log.d(TAG, "Updated events list: $eventsText")
    }

    private fun Date.formatToString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}