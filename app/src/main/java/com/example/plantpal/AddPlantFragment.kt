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
import com.example.plantpal.databinding.FragmentAddPlantBinding
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

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private var photoUri: Uri? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
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

        setupUI()
        setupListeners()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupUI() {
        val frequencyOptions = arrayOf("in a day", "in a week", "once a month")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, frequencyOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFrequency.adapter = adapter

        binding.numberPickerFrequency.minValue = 1
        binding.numberPickerFrequency.maxValue = 5
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

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Failed to start camera", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Photo capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Please enter a plant name", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val plant = hashMapOf(
                    "name" to plantName,
                    "wateringFrequency" to wateringFrequency,
                    "frequencyPeriod" to frequencyPeriod,
                    "notes" to notes,
                    "tags" to tags
                )

                val documentReference = withContext(Dispatchers.IO) {
                    db.collection("plants").add(plant).await()
                }

                val plantId = documentReference.id

                photoUri?.let { uri ->
                    val imageUrl = uploadImage(plantId, uri)
                    withContext(Dispatchers.IO) {
                        db.collection("plants").document(plantId)
                            .update("imageUrl", imageUrl)
                            .await()
                    }
                }

                Toast.makeText(context, "Plant added successfully", Toast.LENGTH_SHORT).show()
                // TODO: Navigate back to the plant list or clear the form
            } catch (e: Exception) {
                Toast.makeText(context, "Error adding plant: ${e.message}", Toast.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}