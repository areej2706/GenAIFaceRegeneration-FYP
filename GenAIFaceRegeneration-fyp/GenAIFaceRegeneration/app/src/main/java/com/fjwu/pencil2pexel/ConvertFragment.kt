package com.fjwu.pencil2pexel

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.fjwu.pencil2pexel.network.ApiClient
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConvertFragment : Fragment() {

    private lateinit var uploadContainer: FrameLayout
    private lateinit var uploadArea: LinearLayout
    private lateinit var uploadIcon: ImageView
    private lateinit var uploadText: TextView
    private lateinit var previewCard: CardView
    private lateinit var previewImage: ImageView
    private lateinit var createButton: MaterialButton
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var resultLabel: TextView
    private lateinit var resultCard: CardView
    private lateinit var resultImage: ImageView
    private lateinit var actionButtons: LinearLayout
    private lateinit var saveButton: MaterialButton
    private lateinit var downloadButton: MaterialButton
    private lateinit var verifyNadraButton: MaterialButton

    private var selectedImageUri: Uri? = null
    private var selectedImageFile: File? = null // Cache the file for faster re-upload
    private var resultBitmap: Bitmap? = null
    private var generatedImageId: String? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                showImagePreview(uri)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            saveImageToGallery()
        } else {
            Toast.makeText(requireContext(), "Permission required to save images", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_convert, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
    }

    private fun initViews(view: View) {
        uploadContainer = view.findViewById(R.id.uploadContainer)
        uploadArea = view.findViewById(R.id.uploadArea)
        uploadIcon = view.findViewById(R.id.uploadIcon)
        uploadText = view.findViewById(R.id.uploadText)
        previewCard = view.findViewById(R.id.previewCard)
        previewImage = view.findViewById(R.id.previewImage)
        createButton = view.findViewById(R.id.createButton)
        loadingIndicator = view.findViewById(R.id.loadingIndicator)
        loadingText = view.findViewById(R.id.loadingText)
        resultLabel = view.findViewById(R.id.resultLabel)
        resultCard = view.findViewById(R.id.resultCard)
        resultImage = view.findViewById(R.id.resultImage)
        actionButtons = view.findViewById(R.id.actionButtons)
        saveButton = view.findViewById(R.id.saveButton)
        downloadButton = view.findViewById(R.id.downloadButton)
        verifyNadraButton = view.findViewById(R.id.verifyNadraButton)
    }

    private fun setupListeners() {
        uploadArea.setOnClickListener {
            openImagePicker()
        }

        previewCard.setOnClickListener {
            openImagePicker()
        }

        createButton.setOnClickListener {
            generateImage()
        }

        saveButton.setOnClickListener {
            saveToHistory()
        }

        downloadButton.setOnClickListener {
            checkPermissionAndSave()
        }

        verifyNadraButton.setOnClickListener {
            showMockNadraDetails()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun showImagePreview(uri: Uri) {
        uploadArea.visibility = View.GONE
        previewCard.visibility = View.VISIBLE
        previewImage.setImageURI(uri)
        resultImage.setImageDrawable(null)
        createButton.isEnabled = true

        // Reset result views and state
        resultLabel.visibility = View.GONE
        resultCard.visibility = View.GONE
        actionButtons.visibility = View.GONE
        verifyNadraButton.visibility = View.GONE
        generatedImageId = null
        resultBitmap = null
    }

    private fun generateImage() {
        selectedImageUri?.let { uri ->
            // Show loading
            loadingIndicator.visibility = View.VISIBLE
            loadingText.visibility = View.VISIBLE
            createButton.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val fileName = getFileNameFromUri(uri) ?: "upload_${System.currentTimeMillis()}.jpg"
                    val file = File(requireContext().cacheDir, fileName)
                    requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(file).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    
                    // Cache the file for faster save later
                    selectedImageFile = file

                    val mimeType = requireContext().contentResolver.getType(uri) ?: "image/jpeg"
                    val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("image", file.name, requestFile)

                    val token = ApiClient.getAuthToken(requireContext())
                    if (token == null) {
                        Toast.makeText(requireContext(), "Please login to generate images", Toast.LENGTH_SHORT).show()
                        loadingIndicator.visibility = View.GONE
                        loadingText.visibility = View.GONE
                        createButton.isEnabled = true
                        return@launch
                    }

                    // Create format parameter to get base64 JSON response
                    val formatBody = "base64".toRequestBody("text/plain".toMediaTypeOrNull())
                    // Don't save to history automatically - let user click save button
                    val saveBody = "false".toRequestBody("text/plain".toMediaTypeOrNull())
                    val qualityBody = "medium".toRequestBody("text/plain".toMediaTypeOrNull())
                    val enhancementBody = "gfpgan".toRequestBody("text/plain".toMediaTypeOrNull())
                    val upscaleBody = "true".toRequestBody("text/plain".toMediaTypeOrNull())

                    val apiService = ApiClient.getApiService(requireContext())
                    val response = apiService.generateImage(
                        token = token,
                        image = body,
                        format = formatBody,
                        save = saveBody,
                        quality = qualityBody,
                        enhancement = enhancementBody,
                        upscale = upscaleBody
                    )

                    if (response.isSuccessful) {
                        val imageResponse = response.body()
                        imageResponse?.let {
                            val decodedString = Base64.decode(it.image, Base64.DEFAULT)
                            resultBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                            generatedImageId = it.imageId // Store the image ID from server

                            loadingIndicator.visibility = View.GONE
                            loadingText.visibility = View.GONE
                            resultLabel.visibility = View.VISIBLE
                            resultCard.visibility = View.VISIBLE
                            actionButtons.visibility = View.VISIBLE
                            verifyNadraButton.visibility = View.VISIBLE
                            resultImage.setImageBitmap(resultBitmap)

                            // Update save button state
                            if (it.saved == true && it.imageId != null) {
                                // Image was saved during generation (shouldn't happen with save=false)
                                saveButton.isEnabled = false
                                saveButton.text = "Saved to History"
                                incrementSavedCount()
                            } else {
                                // Image not saved yet - enable save button
                                saveButton.isEnabled = true
                                saveButton.text = "Save to History"
                            }

                            incrementProcessedCount()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val errorMessage = ApiClient.parseError(errorBody)
                        Toast.makeText(requireContext(), "Error: $errorMessage (${response.code()})", Toast.LENGTH_LONG).show()
                        loadingIndicator.visibility = View.GONE
                        loadingText.visibility = View.GONE
                        createButton.isEnabled = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ConvertFragment", "Error generating image", e)
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    loadingIndicator.visibility = View.GONE
                    loadingText.visibility = View.GONE
                    createButton.isEnabled = true
                }
            }
        }
    }

    private fun saveToHistory() {
        // Save the generated image to server history using the new efficient endpoint
        if (generatedImageId != null) {
            Toast.makeText(requireContext(), "Image already saved to history.", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if we have a generated image
        resultBitmap?.let { bitmap ->
            saveButton.isEnabled = false
            saveButton.text = "Saving..."

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val token = ApiClient.getAuthToken(requireContext())
                    if (token == null) {
                        Toast.makeText(requireContext(), "Please login to save images", Toast.LENGTH_SHORT).show()
                        saveButton.isEnabled = true
                        saveButton.text = "Save to History"
                        return@launch
                    }

                    // Convert bitmap to base64
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    val imageBytes = outputStream.toByteArray()
                    val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

                    // Get filename
                    val fileName = selectedImageUri?.let { uri ->
                        getFileNameFromUri(uri)
                    } ?: "generated_${System.currentTimeMillis()}.png"

                    // Create request
                    val request = com.fjwu.pencil2pexel.network.SaveToHistoryRequest(
                        image = imageBase64,
                        filename = fileName,
                        attributes = "none"
                    )

                    val apiService = ApiClient.getApiService(requireContext())
                    val response = apiService.saveToHistory(token, request)

                    if (response.isSuccessful) {
                        val saveResponse = response.body()
                        saveResponse?.let {
                            generatedImageId = it.imageId
                            saveButton.isEnabled = false
                            saveButton.text = "Saved to History"
                            incrementSavedCount()
                            Toast.makeText(requireContext(), "Image saved to history!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val errorMessage = ApiClient.parseError(errorBody)
                        Toast.makeText(requireContext(), "Error saving: $errorMessage", Toast.LENGTH_LONG).show()
                        saveButton.isEnabled = true
                        saveButton.text = "Save to History"
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ConvertFragment", "Error saving to history", e)
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    saveButton.isEnabled = true
                    saveButton.text = "Save to History"
                }
            }
        } ?: run {
            Toast.makeText(requireContext(), "No image to save", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionAndSave() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageToGallery()
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                saveImageToGallery()
            } else {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun saveImageToGallery() {
        resultBitmap?.let { bitmap ->
            try {
                val filename = "Pencil2Pixel_${System.currentTimeMillis()}.png"
                val outputStream: OutputStream?

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Pencil2Pixel")
                    }
                    val uri = requireContext().contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )
                    outputStream = uri?.let { requireContext().contentResolver.openOutputStream(it) }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val appDir = File(imagesDir, "Pencil2Pixel")
                    if (!appDir.exists()) appDir.mkdirs()
                    val file = File(appDir, filename)
                    outputStream = FileOutputStream(file)
                }

                outputStream?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                Toast.makeText(requireContext(), "Image saved to gallery!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error saving image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun incrementProcessedCount() {
        val sharedPref = requireContext().getSharedPreferences("pencil2pixel_prefs", android.content.Context.MODE_PRIVATE)
        val userId = ApiClient.getUserId(requireContext())
        val key = if (!userId.isNullOrBlank()) "images_processed_${userId}" else "images_processed"
        val currentCount = sharedPref.getInt(key, 0)
        with(sharedPref.edit()) {
            putInt(key, currentCount + 1)
            apply()
        }
    }

    private fun incrementSavedCount() {
        val sharedPref = requireContext().getSharedPreferences("pencil2pixel_prefs", android.content.Context.MODE_PRIVATE)
        val userId = ApiClient.getUserId(requireContext())
        val key = if (!userId.isNullOrBlank()) "images_saved_${userId}" else "images_saved"
        val currentCount = sharedPref.getInt(key, 0)
        with(sharedPref.edit()) {
            putInt(key, currentCount + 1)
            apply()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(index)
                }
            }
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }
        return name
    }

    private fun showMockNadraDetails() {
        val intent = Intent(requireContext(), NadraVerificationActivity::class.java)
        startActivity(intent)
    }
}
