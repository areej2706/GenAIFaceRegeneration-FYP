package com.fjwu.pencil2pexel

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fjwu.pencil2pexel.network.ApiClient
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class HistoryFragment : Fragment() {

    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var clearAllButton: MaterialButton
    private lateinit var adapter: HistoryAdapter
    private val historyItems = mutableListOf<HistoryItem>()

    private var currentPage = 1
    private var totalPages = 1
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        setupListeners()
        loadHistoryFromApi()
    }

    private fun initViews(view: View) {
        historyRecyclerView = view.findViewById(R.id.historyRecyclerView)
        emptyState = view.findViewById(R.id.emptyState)
        loadingIndicator = view.findViewById(R.id.loadingIndicator)
        clearAllButton = view.findViewById(R.id.clearAllButton)
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            items = historyItems,
            onDeleteClick = { position -> confirmDeleteItem(position) },
            onItemClick = { position -> viewFullImage(position) },
            onLoadImage = { imageId, callback -> loadHistoryImage(imageId, callback) }
        )
        historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        historyRecyclerView.adapter = adapter

        // Add scroll listener for pagination
        historyRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && currentPage < totalPages) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 2
                        && firstVisibleItemPosition >= 0) {
                        loadMoreHistory()
                    }
                }
            }
        })
    }

    private fun setupListeners() {
        clearAllButton.setOnClickListener {
            confirmClearAllHistory()
        }
    }

    private fun loadHistoryFromApi(page: Int = 1) {
        val token = ApiClient.getAuthToken(requireContext())
        if (token == null) {
            Toast.makeText(requireContext(), "Please login to view history", Toast.LENGTH_SHORT).show()
            updateEmptyState()
            return
        }

        isLoading = true
        if (page == 1) {
            loadingIndicator.visibility = View.VISIBLE
            historyItems.clear()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val apiService = ApiClient.getApiService(requireContext())
                val response = apiService.getHistory(token, page, 10)

                if (response.isSuccessful && response.body() != null) {
                    val historyResponse = response.body()!!
                    currentPage = historyResponse.pagination.page
                    totalPages = historyResponse.pagination.pages

                    for (historyImage in historyResponse.images) {
                        historyItems.add(
                            HistoryItem(
                                imageId = historyImage.imageId,
                                filename = historyImage.originalFilename,
                                timestamp = historyImage.createdAt,
                                bitmap = null // Will be loaded lazily
                            )
                        )
                    }

                    adapter.notifyDataSetChanged()
                    clearAllButton.visibility = if (historyItems.isNotEmpty()) View.VISIBLE else View.GONE
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = ApiClient.parseError(errorBody)
                    Toast.makeText(requireContext(), "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("HistoryFragment", "Error loading history", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                loadingIndicator.visibility = View.GONE
                updateEmptyState()
            }
        }
    }

    private fun loadMoreHistory() {
        if (currentPage < totalPages) {
            loadHistoryFromApi(currentPage + 1)
        }
    }

    private fun loadHistoryImage(imageId: String, callback: (Bitmap?) -> Unit) {
        val token = ApiClient.getAuthToken(requireContext()) ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val apiService = ApiClient.getApiService(requireContext())
                val response = apiService.getHistoryImage(token, imageId)

                if (response.isSuccessful && response.body() != null) {
                    val bytes = response.body()!!.bytes()
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    callback(bitmap)
                } else {
                    callback(null)
                }
            } catch (e: Exception) {
                android.util.Log.e("HistoryFragment", "Error loading image $imageId", e)
                callback(null)
            }
        }
    }

    private fun confirmDeleteItem(position: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Image")
            .setMessage("Are you sure you want to delete this image?")
            .setPositiveButton("Delete") { _, _ ->
                deleteHistoryItem(position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteHistoryItem(position: Int) {
        val item = historyItems[position]
        val token = ApiClient.getAuthToken(requireContext()) ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val apiService = ApiClient.getApiService(requireContext())
                val response = apiService.deleteHistoryImage(token, item.imageId)

                if (response.isSuccessful) {
                    historyItems.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    updateEmptyState()
                    Toast.makeText(requireContext(), "Image deleted", Toast.LENGTH_SHORT).show()
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = ApiClient.parseError(errorBody)
                    Toast.makeText(requireContext(), "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmClearAllHistory() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear All History")
            .setMessage("Are you sure you want to delete ALL images from your history? This cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                clearAllHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearAllHistory() {
        val token = ApiClient.getAuthToken(requireContext()) ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                loadingIndicator.visibility = View.VISIBLE
                val apiService = ApiClient.getApiService(requireContext())
                val response = apiService.clearHistory(token)

                if (response.isSuccessful) {
                    val deletedCount = response.body()?.deletedCount ?: 0
                    historyItems.clear()
                    adapter.notifyDataSetChanged()
                    updateEmptyState()
                    Toast.makeText(requireContext(), "Cleared $deletedCount images", Toast.LENGTH_SHORT).show()
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = ApiClient.parseError(errorBody)
                    Toast.makeText(requireContext(), "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                loadingIndicator.visibility = View.GONE
            }
        }
    }

    private fun viewFullImage(position: Int) {
        val item = historyItems[position]
        
        // Create a dialog to show the full image
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_full_image, null)
        val fullImageView = dialogView.findViewById<ImageView>(R.id.fullImageView)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.imageLoadingProgress)
        val closeButton = dialogView.findViewById<ImageButton>(R.id.closeButton)
        val downloadButton = dialogView.findViewById<MaterialButton>(R.id.downloadImageButton)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        
        // Load the full resolution image
        if (item.bitmap != null) {
            fullImageView.setImageBitmap(item.bitmap)
            progressBar.visibility = View.GONE
        } else {
            progressBar.visibility = View.VISIBLE
            loadHistoryImage(item.imageId) { bitmap ->
                progressBar.visibility = View.GONE
                if (bitmap != null) {
                    fullImageView.setImageBitmap(bitmap)
                    item.bitmap = bitmap
                } else {
                    Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        downloadButton.setOnClickListener {
            item.bitmap?.let { bitmap ->
                saveImageToGallery(bitmap, item.filename)
            } ?: run {
                Toast.makeText(requireContext(), "Image not loaded yet", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }
    
    private fun saveImageToGallery(bitmap: Bitmap, filename: String) {
        try {
            val displayName = "Pencil2Pixel_${filename}_${System.currentTimeMillis()}.png"
            val outputStream: OutputStream?

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
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
                val file = File(appDir, displayName)
                outputStream = FileOutputStream(file)
            }

            outputStream?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            Toast.makeText(requireContext(), "Image saved to gallery!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEmptyState() {
        if (historyItems.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            historyRecyclerView.visibility = View.GONE
            clearAllButton.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            historyRecyclerView.visibility = View.VISIBLE
            clearAllButton.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        // Only reload if the list is empty (first load or after clearing)
        // This prevents double loading when returning from other fragments
        if (historyItems.isEmpty()) {
            currentPage = 1
            loadHistoryFromApi()
        }
    }
}

data class HistoryItem(
    val imageId: String,
    val filename: String,
    val timestamp: String,
    var bitmap: Bitmap? = null,
    var isLoading: Boolean = false
)

class HistoryAdapter(
    private val items: MutableList<HistoryItem>,
    private val onDeleteClick: (Int) -> Unit,
    private val onItemClick: (Int) -> Unit,
    private val onLoadImage: (String, (Bitmap?) -> Unit) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnailImage: ImageView = view.findViewById(R.id.thumbnailImage)
        val typeText: TextView = view.findViewById(R.id.typeText)
        val timestampText: TextView = view.findViewById(R.id.timestampText)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.typeText.text = item.filename
        holder.timestampText.text = item.timestamp

        // Load image if not already loaded and not currently loading
        if (item.bitmap != null) {
            holder.thumbnailImage.setImageBitmap(item.bitmap)
        } else if (!item.isLoading) {
            holder.thumbnailImage.setImageResource(R.drawable.ic_image)
            item.isLoading = true
            onLoadImage(item.imageId) { bitmap ->
                item.isLoading = false
                if (bitmap != null) {
                    item.bitmap = bitmap
                    holder.thumbnailImage.post {
                        holder.thumbnailImage.setImageBitmap(bitmap)
                    }
                }
            }
        } else {
            // Still loading, show placeholder
            holder.thumbnailImage.setImageResource(R.drawable.ic_image)
        }

        holder.itemView.setOnClickListener {
            onItemClick(holder.adapterPosition)
        }

        holder.deleteButton.setOnClickListener {
            onDeleteClick(holder.adapterPosition)
        }
    }

    override fun getItemCount() = items.size
}

