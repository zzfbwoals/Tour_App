package com.fbwoals.tour_app

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.fbwoals.tour_app.databinding.ActivityEditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditBinding
    private lateinit var dbHelper: DBHelper

    private var recordId: Int = -1
    private var selectedPhotoUri: Uri? = null
    private var extractedLatitude: Double? = null
    private var extractedLongitude: Double? = null
    private var selectedDateString: String = ""

    private var tempPhotoFile: File? = null
    private var tempPhotoUri: Uri? = null

    private val calendar = Calendar.getInstance()

    // 갤러리 픽 처리를 위한 Launcher
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                processAndSetImage(uri)
            }
        }
    }

    // 카메라 픽 처리를 위한 Launcher
    private val captureImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = tempPhotoUri
            if (uri != null) {
                processAndSetImage(uri)
            }
        }
    }

    // 카메라 및 저장소 동적 권한 요청
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val storageGranted = permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: 
                             permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false

        if (cameraGranted || storageGranted) {
            Toast.makeText(this, "권한이 허용되었습니다. 버튼을 다시 클릭해 주세요.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "카메라 촬영 및 이미지 픽을 위해 권한 동의가 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dbHelper = DBHelper(this)

        setSupportActionBar(binding.toolbarEdit)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarEdit.setNavigationOnClickListener { finish() }

        // Intent 데이터 수신으로 추가/수정 모드 분기
        recordId = intent.getIntExtra("RECORD_ID", -1)
        if (recordId != -1) {
            binding.toolbarEdit.title = "추억 수정하기"
            loadRecordData(recordId)
        } else {
            binding.toolbarEdit.title = "추억 기록하기"
            setCurrentDate()
        }

        binding.layoutAddPhoto.setOnClickListener {
            checkPermissionsAndShowPhotoOptions()
        }
        binding.ivEditPhoto.setOnClickListener {
            checkPermissionsAndShowPhotoOptions()
        }
        binding.btnPhotoCamera.setOnClickListener { takePhoto() }
        binding.btnPhotoGallery.setOnClickListener { pickFromGallery() }

        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnSave.setOnClickListener {
            saveRecord()
        }
    }

    private fun checkPermissionsAndShowPhotoOptions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isEmpty()) {
            binding.layoutPhotoOptions.visibility = View.VISIBLE
        } else {
            requestPermissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    private fun pickFromGallery() {
        binding.layoutPhotoOptions.visibility = View.GONE
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun takePhoto() {
        binding.layoutPhotoOptions.visibility = View.GONE
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                val tempFile = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
                    tempPhotoFile = this
                }
                
                // FileProvider Uri 발급
                val authority = "${packageName}.fileprovider"
                val photoUri = FileProvider.getUriForFile(this, authority, tempFile)
                tempPhotoUri = photoUri
                
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                captureImageLauncher.launch(cameraIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "카메라 파일 생성에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "실행할 수 있는 카메라가 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // ExifInterface 위경도 GPS 데이터 비동기 추출 & 이미지 스케일 디코딩 (+2점 가산점)
    private fun processAndSetImage(uri: Uri) {
        selectedPhotoUri = uri
        binding.progressOverlay.visibility = View.VISIBLE
        binding.tvProgressMsg.text = "사진 분석 및 위치 데이터 추출 중..."

        lifecycleScope.launch {
            var locationExtracted = false
            var lat: Double? = null
            var lng: Double? = null

            val bitmap = withContext(Dispatchers.IO) {
                try {
                    // 1. ExifInterface를 통한 GPS 데이터 파싱
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val exif = ExifInterface(inputStream)
                        val latLong = FloatArray(2)
                        if (exif.getLatLong(latLong)) {
                            lat = latLong[0].toDouble()
                            lng = latLong[1].toDouble()
                            locationExtracted = true
                        }
                    }

                    // 2. 비동기 이미지 리사이징 (OOM 회피)
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 2
                        }
                        BitmapFactory.decodeStream(inputStream, null, options)
                    }
                } catch (e: Exception) {
                    null
                }
            }

            binding.progressOverlay.visibility = View.GONE

            if (bitmap != null) {
                binding.ivEditPhoto.setImageBitmap(bitmap)
                binding.layoutAddPhoto.visibility = View.GONE
                binding.layoutPhotoOptions.visibility = View.GONE

                if (locationExtracted && lat != null && lng != null) {
                    extractedLatitude = lat
                    extractedLongitude = lng
                    binding.layoutGpsStatus.visibility = View.VISIBLE
                    binding.tvGpsInfo.text = "위치 데이터 추출 성공"
                    binding.tvLatlongDisplay.text = String.format("위도: %.4f, 경도: %.4f", lat, lng)
                    Toast.makeText(this@EditActivity, "사진에서 GPS 정보를 획득했습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    extractedLatitude = null
                    extractedLongitude = null
                    binding.layoutGpsStatus.visibility = View.GONE
                    binding.tvLatlongDisplay.text = "사진에 GPS 위치 정보가 없습니다."
                }
            } else {
                Toast.makeText(this@EditActivity, "이미지를 처리하지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setCurrentDate() {
        val today = Date()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        selectedDateString = dateFormat.format(today)
        binding.btnSelectDate.text = selectedDateString
    }

    private fun showDatePicker() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val selectedCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, selectedYear)
                set(Calendar.MONTH, selectedMonth)
                set(Calendar.DAY_OF_MONTH, selectedDay)
            }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            selectedDateString = dateFormat.format(selectedCalendar.time)
            binding.btnSelectDate.text = selectedDateString
        }, year, month, day).show()
    }

    private fun loadRecordData(id: Int) {
        binding.progressOverlay.visibility = View.VISIBLE
        binding.tvProgressMsg.text = "기록 불러오는 중..."

        lifecycleScope.launch {
            val record = withContext(Dispatchers.IO) {
                dbHelper.getRecordById(id)
            }
            binding.progressOverlay.visibility = View.GONE

            if (record != null) {
                binding.etPlaceName.setText(record.place)
                selectedDateString = record.visitDate
                binding.btnSelectDate.text = record.visitDate
                binding.etMemo.setText(record.memo)

                if (record.photoUri.isNotEmpty()) {
                    selectedPhotoUri = Uri.parse(record.photoUri)
                    loadSavedImage(record.photoUri)
                }

                if (record.latitude != null && record.longitude != null) {
                    extractedLatitude = record.latitude
                    extractedLongitude = record.longitude
                    binding.layoutGpsStatus.visibility = View.VISIBLE
                    binding.tvGpsInfo.text = "위치 데이터 연동됨"
                    binding.tvLatlongDisplay.text = String.format("위도: %.4f, 경도: %.4f", record.latitude, record.longitude)
                }
            } else {
                Toast.makeText(this@EditActivity, "기록 정보가 손상되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadSavedImage(uriString: String) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(uriString)
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 2
                        }
                        BitmapFactory.decodeStream(inputStream, null, options)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                binding.ivEditPhoto.setImageBitmap(bitmap)
                binding.layoutAddPhoto.visibility = View.GONE
            }
        }
    }

    private fun saveRecord() {
        val place = binding.etPlaceName.text.toString().trim()
        val memo = binding.etMemo.text.toString().trim()
        val date = selectedDateString

        if (place.isEmpty()) {
            binding.etPlaceName.error = "여행지 이름을 입력해 주세요."
            return
        }

        val photoUriStr = selectedPhotoUri?.toString() ?: ""

        val record = TravelRecord(
            id = if (recordId == -1) 0 else recordId,
            place = place,
            visitDate = date,
            memo = memo,
            photoUri = photoUriStr,
            latitude = extractedLatitude,
            longitude = extractedLongitude
        )

        binding.progressOverlay.visibility = View.VISIBLE
        binding.tvProgressMsg.text = "저장하는 중..."

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                if (recordId == -1) {
                    dbHelper.insertRecord(record) > 0
                } else {
                    dbHelper.updateRecord(record) > 0
                }
            }
            binding.progressOverlay.visibility = View.GONE

            if (success) {
                Toast.makeText(this@EditActivity, "저장 완료되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@EditActivity, "저장 중 에러가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
