package com.inblocks.meterscan

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.inblocks.meterscan.Constants.LABELS_PATH
import com.inblocks.meterscan.Constants.LABELS_SEC_PATH
import com.inblocks.meterscan.Constants.MODEL_PATH
import com.inblocks.meterscan.Constants.NUMBER_PATH
import com.inblocks.meterscan.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.io.encoding.Base64

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var camera: Camera? = null
    private lateinit var capturedImageView: ImageView
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewView: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var detector: MeterDetector? = null
    val targetAspectRatio = 1.0f / 1.0f
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture
    private lateinit var bitmapData :  Bitmap


    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                // Convert URI to Bitmap and display in ImageView
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                val matrix = Matrix()
//                matrix.postRotate(90.toFloat())
                val rotatedImg = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
                )
                binding.capturedImageView.setImageBitmap(rotatedImg)
                binding.previewView.visibility = View.GONE
                binding.capturedImageView.visibility = View.VISIBLE
                binding.scanningLine.animation.cancel()
                binding.scanningLine.visibility =View.GONE
                binding.buttonView.text = "Do OCR"
                bitmapData  = rotatedImg

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor.execute {
            detector = MeterDetector(baseContext, MODEL_PATH, LABELS_PATH)
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        ///this below code is to animate scanner
        animateScannerLine()

//        binding.gallery.setOnClickListener{
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
//                != PackageManager.PERMISSION_GRANTED
//            ) {
//                // Request permission
//                ActivityCompat.requestPermissions(
//                    this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 100
//                )
//            } else {
//                // Open gallery
//                pickImageLauncher.launch("image/*")
//            }
//
//        }

        binding.buttonView.setOnClickListener{
            if(binding.buttonView.text == "Do OCR")
            {
                scanPhoto()
            }
            else {
                capturePhoto()
            }
        }

        binding.history.setOnClickListener {
            Intent(this, HistoryView::class.java).apply {
                startActivity(this)
            }

        }

        binding.upload.setOnClickListener{
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                // Request permission
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 100
                )
            } else {
                // Open gallery
                pickImageLauncher.launch("image/*")
            }
        }


    }

    private fun scanPhoto() {
        var processBitmap = addLetterboxToImage(
                bitmapData,
                targetAspectRatio,
                R.color.gray
            )
        var bestBoxDisplays = detector?.detect(processBitmap)
        if (bestBoxDisplays != null) {

            var displayBitmap = processDisplays(bestBoxDisplays!!, processBitmap)
            var processDisplayBitmap = addLetterboxToImage(
                displayBitmap!!,
                targetAspectRatio,
                R.color.gray
            )
            detector = MeterDetector(baseContext, NUMBER_PATH, LABELS_SEC_PATH)
            var displayBox = detector?.detect(processDisplayBitmap)
            processMeterOrder(displayBox!!, displayBitmap);

        }
        else
        {
            binding.capturedImageView.visibility =ImageView.GONE
            binding.previewView.visibility = ImageView.VISIBLE
            binding.scanningLine.visibility = View.VISIBLE
            binding.buttonView.text = "Capture"
            animateScannerLine()
//            Toast.makeText(this@MainActivity, "Not a meter", Toast.LENGTH_SHORT).show()
            showNoMeterDialog()
        }
    }
    private fun capturePhoto()
    {
        val file = File(
            externalMediaDirs.firstOrNull(),
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.CHINA)
                .format(System.currentTimeMillis()) + ".jpg"
        )


        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Show the saved image in the ImageView

                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    val matrix = Matrix()
                    matrix.postRotate(90.toFloat())
                    val rotatedImg = Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        matrix,
                        true
                    )
                    binding.capturedImageView.setImageBitmap(rotatedImg)
                    binding.previewView.visibility = View.GONE
                    binding.capturedImageView.visibility = View.VISIBLE
                    binding.scanningLine.animation.cancel()
                    binding.scanningLine.visibility =View.GONE
                   binding.buttonView.text = "Do OCR"



                    bitmapData  = rotatedImg


                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Failed to capture photo: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }



    private fun animateScannerLine() {
        val animation = TranslateAnimation(
            0f, 0f, -500f, 350f // Adjust height to match the QR code scanning frame
        ).apply {
            duration = 1000 // 1 seconds
            repeatMode = TranslateAnimation.REVERSE
            repeatCount = TranslateAnimation.INFINITE
        }

        binding.scanningLine.startAnimation(animation)

    }

        fun saveDataList(meterData: MeterData, key: String?) {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor: SharedPreferences.Editor = prefs.edit()
        val gson = Gson()
        val json: String = gson.toJson(meterData)
        editor.putString(key, json)
        editor.apply()
    }

    fun getDataList(key: String?):  MeterData? {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val gson = Gson()
        val json: String = prefs.getString(key!!, null).toString()

        var data =  gson.fromJson(json, MeterData().javaClass)
        return data
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setTargetRotation(binding.previewView .display.rotation).
                    build()

                val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA


                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }, ContextCompat.getMainExecutor(this))
        }, ContextCompat.getMainExecutor(this))
    }

   @RequiresApi(Build.VERSION_CODES.O)
   private fun convertBitmapToBase64(bitmap: Bitmap):  String {
       val baos: ByteArrayOutputStream = ByteArrayOutputStream()
       bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
       val b: ByteArray = baos.toByteArray()

       return java.util.Base64.getEncoder().encodeToString(b)
   }
    private fun showNoMeterDialog() {
        // Inflate the custom dialog layout
        val dialogView: View = LayoutInflater.from(this).inflate(R.layout.no_meter_dialog, null)

        // Initialize buttons
        val cancelButton = dialogView.findViewById<Button>(R.id.okayButton)

        // Create and show the dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showUnitialog(
        value: String, unit: String, mdDetectedValue: Boolean, displayImage: Bitmap,
        mdConf: Float, valConf: Float, uConf: Float,
    ) {
        // Inflate the custom dialog layout
        val dialogView: View = LayoutInflater.from(this).inflate(R.layout.custom_dialog, null)
        var meterDataModel = MeterDataModel()
        // Initialize buttons
        val cancelButton = dialogView.findViewById<Button>(R.id.goBack)
        val okButton = dialogView.findViewById<Button>(R.id.approve)

        val unitText =  dialogView.findViewById<TextView>(R.id.unit)
        val valText =  dialogView.findViewById<TextView>(R.id.value)
        val mdDetected = dialogView.findViewById<TextView>(R.id.mdDetected)
        val displayImageHolder = dialogView.findViewById<ImageView>(R.id.displayLCD)
        val mdDetectedConf = dialogView.findViewById<TextView>(R.id.mdDetectedConf)
        val unitConf = dialogView.findViewById<TextView>(R.id.unitConf)
        val valueConf = dialogView.findViewById<TextView>(R.id.valueConf)

        displayImageHolder.setImageBitmap(displayImage)
        meterDataModel.meterImage = convertBitmapToBase64(displayImage)
        if (unit.equals("")) {
            unitText.text = "NA"
            unitConf.text = "NA"
        }
        else {
            unitText.text = unit
            unitConf.text = (uConf*100).toString() + "%"
        }

        if (value.equals("")){
            valText.text = "NA"
            valueConf.text = "NA"
        }
        else {
            valText.text = value
            valueConf.text = (valConf*100).toString() + "%"
        }

        if(mdDetectedValue)
        {
            mdDetected.text =  "Yes"
            meterDataModel.maxDemand = "Yes"
            meterDataModel.demandPer = (mdConf*100).toString() + "%"
            mdDetectedConf.text = (mdConf*100).toString() + "%"
        }
        else
        {
            mdDetected.text =  "No"
            mdDetectedConf.text = "NA"
            meterDataModel.maxDemand = "NO"
            meterDataModel.demandPer = "NA"
        }
        meterDataModel.unit =  unit
        meterDataModel.unitPer = (uConf*100).toString() + "%"
        meterDataModel.reading =  value
        meterDataModel.readingPer = (valConf*100).toString() + "%"


        // Create and show the dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)

            .create()

       cancelButton.setOnClickListener {
            // Handle "Join Waitlist" action
           binding.capturedImageView.visibility =ImageView.GONE
           binding.previewView.visibility = ImageView.VISIBLE
           binding.scanningLine.visibility = View.VISIBLE
           binding.buttonView.text = "Capture"
           animateScannerLine()
           detector = MeterDetector(baseContext, MODEL_PATH, LABELS_PATH)
            dialog.dismiss()
        }

        okButton.setOnClickListener {
            // Handle "Join Waitlist" action

             var meterDataList =  getDataList("meterData")
            if(meterDataList == null)
            {
                var meterDataList = MeterData()
                var arrayList  = ArrayList<MeterDataModel>()
                arrayList.add(meterDataModel)
                meterDataList.data =  arrayList
               saveDataList(meterDataList,"meterData")
            }
            else
            {
                meterDataList.data.add(meterDataModel)
                saveDataList(meterDataList,"meterData")
            }
            binding.capturedImageView.visibility =ImageView.GONE
            binding.previewView.visibility = ImageView.VISIBLE
            binding.scanningLine.visibility = View.VISIBLE
            binding.buttonView.text = "Capture"
            animateScannerLine()
            detector = MeterDetector(baseContext, MODEL_PATH, LABELS_PATH)







            dialog.dismiss()
        }
        dialog.show()
    }


    fun processMeterOrder(boundingBoxes: List<BoundingBox>, display: Bitmap) {
        val mapBox = mutableMapOf<Float, List<Float>>()
        var finalOutput = ""
        var classList = listOf(
            "0",
            "1",
            "2",
            "3",
            "4",
            "5",
            "6",
            "7",
            "8",
            "9",
            ".",
            "KVaH",
            "KwH",
            "MD"
        )
        boundingBoxes.forEach { values ->
            var centerPoint = values.x1 + (values.w / 2)
            mapBox[centerPoint] = listOf(values.cls.toFloat(), values.cnf, values.h)
        }

        var sortedMapValue = mapBox.toSortedMap()
        var unit = ""
        var md = false
        var numCnf = 0.toFloat()
        var mdCnf = 0.toFloat()
        var unitCnf  = 0.toFloat()
       var previousHeight = 0.toFloat()

        var numCount = 0
        sortedMapValue.forEach{ element ->



            if((previousHeight * 0.75) > element.value.get(2) && listOf(10, 11, 12, 13).contains(element.value.get(0).toInt()).not() )
            {
                if(finalOutput.contains('.').not())
                {
                    finalOutput = finalOutput + "."
                }
            }

            ///Units checking condition
            if(listOf(11, 12).contains(element.value.get(0).toInt()))
            {
               unit =  classList[element.value.get(0).toInt()]
                unitCnf = element.value.get(1)
            }

            else if(element.value.get(0).toInt() == 13)
            {
                md = true
                mdCnf = element.value.get(1)
            }

            else {
                finalOutput = finalOutput + classList[element.value.get(0).toInt()]
                numCount += 1
                numCnf += element.value.get(1)
            }
            previousHeight = element.value.get(2)
        }

        showUnitialog(finalOutput, unit, md, display, mdCnf, numCnf/numCount, unitCnf)
//        Toast.makeText(this, finalOutput, Toast.LENGTH_LONG).show()

    }
    private fun processDisplays(boundingBoxes: List<BoundingBox>, bitmapImage: Bitmap): Bitmap? {


        var bitmapImageWidth = bitmapImage.width
        var bitmapImageHeight = bitmapImage.height

        boundingBoxes.forEach { boundingBoxVal ->


            var top = boundingBoxVal.y1 * bitmapImageHeight
            var left = boundingBoxVal.x1 * bitmapImageWidth
            var width = boundingBoxVal.w * bitmapImageWidth
            var height = boundingBoxVal.h * bitmapImageHeight
            var display = Bitmap.createBitmap(
                bitmapImage,
                left.toInt(),
                top.toInt(),
                width.toInt(),
                height.toInt()
            )
            return display
        }


        return null

    }
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it[Manifest.permission.CAMERA] == true) {
            startCamera()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImageLauncher.launch("image/*")
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun addLetterboxToImage(
        bitmap: Bitmap,
        targetAspectRatio: Float,
        backgroundColor: Int,
    ): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val originalAspectRatio = originalWidth.toFloat() / originalHeight

        var newWidth = originalWidth
        var newHeight = originalHeight

        if (originalAspectRatio > targetAspectRatio) {
            // Add padding to the top and bottom
            newHeight = (originalWidth / targetAspectRatio).toInt()
        } else {
            // Add padding to the left and right
            newWidth = (originalHeight * targetAspectRatio).toInt()
        }

        // Create a new bitmap with the target dimensions
        val resultBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        // Fill the canvas with the background color
        canvas.drawColor(backgroundColor)

        // Draw the original image centered on the canvas
        val left = (newWidth - originalWidth) / 2f
        val top = (newHeight - originalHeight) / 2f
        canvas.drawBitmap(bitmap, left, top, Paint())
        canvas.height

        return resultBitmap
    }

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_MEDIA_IMAGES
        ).toTypedArray()
    }
}