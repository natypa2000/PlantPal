package com.example.plantpal

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.plantpal.databinding.FragmentAddPlantBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AddPlantFragment : Fragment() {

    private var _binding: FragmentAddPlantBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var auth: FirebaseAuth

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private var photoUri: Uri? = null
    private var plantId: String? = null
    private var environmentId: String? = null

    private val args: AddPlantFragmentArgs by navArgs()

    private var userRole: String = ""

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showToast("Camera permission is required to take photos")
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            photoUri = it
            binding.imageViewPlant.setImageURI(it)
            binding.imageViewPlant.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPlantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        auth = FirebaseAuth.getInstance()

        plantId = args.plantId
        environmentId = args.environmentId

        setupUI()
        setupListeners()
        checkUserPermissions()

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (plantId != null) {
            loadPlantData(plantId!!)
        }
    }

    private fun setupUI() {
        val frequencyOptions = arrayOf("Day", "Week", "Month")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, frequencyOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFrequency.adapter = adapter

        binding.numberPickerFrequency.minValue = 1
        binding.numberPickerFrequency.maxValue = 30
    }

    private fun setupListeners() {
        binding.buttonTakePhoto.setOnClickListener {
            requestCameraPermission()
        }

        binding.buttonChoosePhoto.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.buttonSave.setOnClickListener {
            savePlant()
        }
    }

    private fun checkUserPermissions() {
        val currentUserId = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val environmentDoc = environmentId?.let {
                    db.collection("environments").document(it).get().await()
                }

                if (environmentDoc != null && environmentDoc.exists()) {
                    val users = environmentDoc.get("users") as? Map<String, String>
                    userRole = users?.get(currentUserId) ?: "viewer"

                    withContext(Dispatchers.Main) {
                        updateUIBasedOnRole()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showSnackbar("Environment not found")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("Error checking permissions: ${e.message}")
                }
            }
        }
    }

    private fun updateUIBasedOnRole() {
        val isCreatorOrEditor = userRole == "creator" || userRole == "editor"
        binding.buttonSave.isEnabled = isCreatorOrEditor
        binding.buttonTakePhoto.isEnabled = isCreatorOrEditor
        binding.buttonChoosePhoto.isEnabled = isCreatorOrEditor
        binding.editTextPlantName.isEnabled = isCreatorOrEditor
        binding.editTextNotes.isEnabled = isCreatorOrEditor
        binding.numberPickerFrequency.isEnabled = isCreatorOrEditor
        binding.spinnerFrequency.isEnabled = isCreatorOrEditor
        binding.editTextTags.isEnabled = isCreatorOrEditor

        if (!isCreatorOrEditor) {
            showSnackbar("You are in view-only mode")
        }
    }

    private fun loadPlantData(plantId: String) {
        db.collection("plants").document(plantId).get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val plant = document.toObject(Plant::class.java)
                    plant?.let {
                        populateUI(it)
                    }
                }
            }
            .addOnFailureListener { exception ->
                showSnackbar("Error loading plant: ${exception.message}")
            }
    }

    private fun populateUI(plant: Plant) {
        binding.editTextPlantName.setText(plant.name)
        binding.editTextNotes.setText(plant.notes)
        binding.numberPickerFrequency.value = plant.wateringFrequency
        binding.spinnerFrequency.setSelection(getFrequencyPeriodIndex(plant.frequencyPeriod))
        binding.editTextTags.setText(plant.tags.joinToString(", "))

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    java.net.URL(plant.imageUrl).openStream().use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }
                }
                withContext(Dispatchers.Main) {
                    binding.imageViewPlant.setImageBitmap(bitmap)
                    binding.imageViewPlant.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("Failed to load image")
                }
            }
        }

        binding.buttonSave.text = "Update Plant"
    }

    private fun getFrequencyPeriodIndex(frequencyPeriod: String): Int {
        return when (frequencyPeriod) {
            "Day" -> 0
            "Week" -> 1
            "Month" -> 2
            else -> 0
        }
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showSnackbar("Camera permission is required to take photos")
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
                binding.viewFinder.visibility = View.VISIBLE
                binding.buttonCapturePhoto.visibility = View.VISIBLE
                binding.buttonCapturePhoto.setOnClickListener { takePhoto() }
            } catch (exc: Exception) {
                showSnackbar("Failed to start camera")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            requireContext().externalMediaDirs.first(),
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    showSnackbar("Photo capture failed: ${exc.message}")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    photoUri = savedUri
                    binding.imageViewPlant.setImageURI(savedUri)
                    binding.imageViewPlant.visibility = View.VISIBLE
                    binding.viewFinder.visibility = View.GONE
                    binding.buttonCapturePhoto.visibility = View.GONE
                }
            }
        )
    }

    private fun savePlant() {
        val plantName = binding.editTextPlantName.text.toString()
        val wateringFrequency = binding.numberPickerFrequency.value
        val frequencyPeriod = binding.spinnerFrequency.selectedItem.toString()
        val notes = binding.editTextNotes.text.toString()
        val tags = binding.editTextTags.text.toString().split(",").map { it.trim() }

        if (plantName.isBlank()) {
            showSnackbar("Please enter a plant name")
            return
        }

        lifecycleScope.launch {
            try {
                val plant = hashMapOf(
                    "name" to plantName,
                    "wateringFrequency" to wateringFrequency,
                    "frequencyPeriod" to frequencyPeriod,
                    "notes" to notes,
                    "tags" to tags,
                    "creatorId" to auth.currentUser?.uid,
                    "environmentId" to (environmentId ?: "")
                )

                val documentId = plantId ?: db.collection("plants").document().id

                photoUri?.let { uri ->
                    val imageUrl = uploadImage(documentId, uri)
                    plant["imageUrl"] = imageUrl
                }

                withContext(Dispatchers.IO) {
                    db.collection("plants").document(documentId).set(plant).await()
                }

                showSnackbar("Plant saved successfully")
                findNavController().popBackStack()
            } catch (e: Exception) {
                showSnackbar("Error saving plant: ${e.message}")
            }
        }
    }

    private suspend fun uploadImage(plantId: String, imageUri: Uri): String {
        return withContext(Dispatchers.IO) {
            val storageRef = storage.reference.child("plant_images/$plantId.jpg")
            storageRef.putFile(imageUri).await()
            storageRef.downloadUrl.await().toString()
        }
    }

    private fun showSnackbar(message: String) {
        view?.let {
            com.google.android.material.snackbar.Snackbar.make(it, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}