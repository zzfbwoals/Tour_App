package com.fbwoals.tour_app

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class EditActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var db: TravelDbHelper
    private lateinit var placeEdit: EditText
    private lateinit var dateEdit: TextView
    private lateinit var memoEdit: EditText
    private lateinit var photoPreview: ImageView
    private lateinit var deleteButton: android.widget.Button
    private lateinit var photoList: RecyclerView
    private lateinit var photoAdapter: EditPhotoAdapter
    private var editingId: Long = 0
    private val photoUris = mutableListOf<String>()
    private var coverPhotoUri: String? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private var selectedDate: LocalDate = LocalDate.now()
    private var pendingCameraUri: Uri? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        scope.launch {
            val copiedUris = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> copyImageToInternalStorage(uri) ?: uri.toString() }
            }
            copiedUris.forEach(::addPhoto)
        }
    }

    private val cameraCapture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingCameraUri?.toString()?.let(::addPhoto)
        }
    }

    private val placePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        placeEdit.setText(data.getStringExtra(PlacePickerActivity.EXTRA_PLACE_NAME).orEmpty())
        selectedLatitude = data.getDoubleExtra(PlacePickerActivity.EXTRA_PLACE_LATITUDE, Double.NaN)
            .takeUnless { it.isNaN() }
        selectedLongitude = data.getDoubleExtra(PlacePickerActivity.EXTRA_PLACE_LONGITUDE, Double.NaN)
            .takeUnless { it.isNaN() }
        data.getStringExtra(PlacePickerActivity.EXTRA_PLACE_PHOTO_URI)
            ?.takeIf { it.isNotBlank() }
            ?.let(::addPhoto)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        db = TravelDbHelper(this)
        editingId = intent.getLongExtra(FeedFragment.EXTRA_RECORD_ID, 0)
        bindViews()
        dateEdit.text = selectedDate.format(DB_DATE)
        if (editingId > 0) loadRecord(editingId)
    }

    override fun onDestroy() {
        scope.cancel()
        db.close()
        super.onDestroy()
    }

    private fun bindViews() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.saveButton).setOnClickListener { saveRecord() }
        findViewById<MaterialButton>(R.id.photoButton).setOnClickListener { choosePhotoSource() }
        findViewById<ImageButton>(R.id.placeMapButton).setOnClickListener {
            placePicker.launch(Intent(this, PlacePickerActivity::class.java))
        }
        placeEdit = findViewById(R.id.placeEdit)
        dateEdit = findViewById(R.id.dateEdit)
        memoEdit = findViewById(R.id.memoEdit)
        photoPreview = findViewById(R.id.photoPreview)
        deleteButton = findViewById(R.id.deleteButton)
        photoList = findViewById(R.id.photoList)
        photoAdapter = EditPhotoAdapter { selectedUri ->
            coverPhotoUri = selectedUri
            refreshPhotos()
        }
        photoList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        photoList.adapter = photoAdapter
        dateEdit.setOnClickListener { showDatePicker() }
        deleteButton.setOnClickListener { confirmDelete() }
        findViewById<TextView>(R.id.editTitle).text = if (editingId > 0) "기록 수정" else "새 기록"
    }

    private fun loadRecord(id: Long) {
        scope.launch {
            val record = withContext(Dispatchers.IO) { db.getById(id) } ?: return@launch finish()
            placeEdit.setText(record.place)
            memoEdit.setText(record.memo)
            selectedDate = runCatching { LocalDate.parse(record.visitDate, DB_DATE) }.getOrDefault(LocalDate.now())
            dateEdit.text = selectedDate.format(DB_DATE)
            photoUris.clear()
            photoUris.addAll(record.photoUris)
            coverPhotoUri = record.coverPhotoUri ?: record.displayPhotoUri
            selectedLatitude = record.latitude
            selectedLongitude = record.longitude
            refreshPhotos()
            deleteButton.visibility = android.view.View.VISIBLE
        }
    }

    private fun choosePhotoSource() {
        AlertDialog.Builder(this)
            .setTitle("사진 추가")
            .setItems(arrayOf("갤러리에서 여러 장 선택", "카메라로 촬영")) { _, which ->
                if (which == 0) {
                    imagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                } else {
                    val imageFile = createCameraFile()
                    pendingCameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
                    pendingCameraUri?.let(cameraCapture::launch)
                }
            }
            .show()
    }

    private fun addPhoto(uri: String) {
        if (photoUris.none { it == uri }) {
            photoUris += uri
        }
        if (coverPhotoUri.isNullOrBlank()) {
            coverPhotoUri = uri
        }
        refreshPhotos()
    }

    private fun refreshPhotos() {
        val coverUri = coverPhotoUri ?: photoUris.firstOrNull()
        coverPhotoUri = coverUri
        photoPreview.loadTravelImage(coverUri)
        photoList.visibility = if (photoUris.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        photoAdapter.submitList(photoUris, coverUri)
    }

    private fun createCameraFile(): File {
        val dir = File(filesDir, "images").apply { mkdirs() }
        return File(dir, "travel_${System.currentTimeMillis()}.jpg")
    }

    private fun copyImageToInternalStorage(sourceUri: Uri): String? {
        return runCatching {
            val dir = File(filesDir, "images").apply { mkdirs() }
            val file = File(dir, "picked_${System.currentTimeMillis()}_${sourceUri.lastPathSegment.hashCode()}.jpg")
            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                dateEdit.text = selectedDate.format(DB_DATE)
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).show()
    }

    private fun saveRecord() {
        val place = placeEdit.text.toString().trim()
        if (place.isBlank()) {
            placeEdit.error = "여행지 이름을 입력하세요."
            return
        }
        val memo = memoEdit.text.toString().trim()
        scope.launch {
            val coverUri = coverPhotoUri ?: photoUris.firstOrNull()
            val location = withContext(Dispatchers.IO) { ExifLocationReader.readLocation(this@EditActivity, coverUri) }
            val latitude = selectedLatitude ?: location?.first
            val longitude = selectedLongitude ?: location?.second
            val record = TravelRecord(
                no = editingId,
                place = place,
                visitDate = selectedDate.format(DB_DATE),
                memo = memo,
                photoUri = coverUri,
                latitude = latitude,
                longitude = longitude,
                photoUris = photoUris.toList(),
                coverPhotoUri = coverUri
            )
            withContext(Dispatchers.IO) {
                if (editingId > 0) db.update(record) else db.insert(record)
            }
            Toast.makeText(this@EditActivity, "기록을 저장했습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("기록 삭제")
            .setMessage("이 여행 기록을 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) { db.delete(editingId) }
                    finish()
                }
            }
            .show()
    }

    companion object {
        val DB_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
