package com.andre.tflite.classification

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.andre.tflite.classification.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding

    private var selectedBitmap: Bitmap? = null
    private var selectedModel: String = "segmentation"
    private var classifier: Classifier? = null
    private val segmentationClasses = arrayOf("Pet", "Background", "Border")

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            selectedBitmap = bitmap
            binding.imageView.setImageBitmap(bitmap)
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            selectedBitmap = bitmap
            binding.imageView.setImageBitmap(bitmap)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openGallery()
        } else {
            Toast.makeText(this, "Permission denied to read your storage.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (classifier == null) {
            classifier = Classifier(assets, "segmentation_model.tflite", 128, 3)
        }

        binding.modelSelector.setOnCheckedChangeListener { _, checkedId ->
            selectedModel = if (checkedId == binding.fp32Model.id) "segmentation" else "segmentation"
            val modelFile = "segmentation_model.tflite"
            classifier = Classifier(assets, modelFile, 128, 3)
            Toast.makeText(this, "Using Segmentation Model", Toast.LENGTH_SHORT).show()
        }

        binding.btnCamera.setOnClickListener {
            takePictureLauncher.launch(null)
        }

        binding.btnGallery.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2) {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                openGallery()
            }
        }

        binding.btnPredict.setOnClickListener {
            binding.txtResult.text = "Selected model: Semantic Segmentation"
            runSegmentation()
        }
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun runSegmentation() {
        val bitmap = selectedBitmap ?: run {
            binding.txtResult.text = "Please select an image first"
            return
        }
        
        try {
            val result = classifier?.segment(bitmap)
            if (result != null) {
                // Mostrar a máscara de segmentação
                binding.imageView.setImageBitmap(result.coloredMask)
                
                // Calcular estatísticas da segmentação
                val mask = result.segmentationMask
                val classCounts = IntArray(segmentationClasses.size)
                val totalPixels = mask.size * mask[0].size
                
                for (i in mask.indices) {
                    for (j in mask[i].indices) {
                        val classIndex = mask[i][j]
                        if (classIndex < classCounts.size) {
                            classCounts[classIndex]++
                        }
                    }
                }
                
                val statistics = segmentationClasses.mapIndexed { index, className ->
                    val percentage = (classCounts[index].toFloat() / totalPixels * 100)
                    "$className: ${"%.1f".format(java.util.Locale.US, percentage)}%"
                }.joinToString("\n")
                
                binding.txtResult.text = """
                    Segmentação Semântica
                    Confiança: ${"%.1f".format(java.util.Locale.US, result.confidence * 100)}%
                    
                    Distribuição de Classes:
                    $statistics
                    
                    Cores:
                    🔴 Pet (Vermelho)
                    🟢 Fundo (Verde) 
                    🔵 Borda (Azul)
                """.trimIndent()
                
            } else {
                binding.txtResult.text = "Segmentation failed"
            }
        } catch (e: Exception) {
            binding.txtResult.text = "Error during segmentation: ${e.message}"
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()
    }
}