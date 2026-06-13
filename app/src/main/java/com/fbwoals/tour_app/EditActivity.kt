package com.fbwoals.tour_app

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

// 여행 기록을 새로 추가하거나 기존 기록을 수정하는 화면입니다.
class EditActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var db: TravelDbHelper
    private lateinit var placeEdit: EditText
    private lateinit var dateEdit: TextView
    private lateinit var memoEdit: EditText
    private lateinit var editRoot: ScrollView
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

    // 갤러리에서 여러 이미지를 선택하고 앱 내부 저장소로 복사합니다.
    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        scope.launch {
            val copiedUris = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> copyImageToInternalStorage(uri) ?: uri.toString() }
            }
            copiedUris.forEach(::addPhoto)
        }
    }

    // 카메라 촬영 결과가 성공이면 촬영 파일 URI를 사진 목록에 추가합니다.
    private val cameraCapture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingCameraUri?.toString()?.let(::addPhoto)
        }
    }

    // 카메라 권한 요청 결과에 따라 촬영을 계속하거나 안내 문구를 표시합니다.
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startCameraCapture()
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 지도에서 선택한 장소명/좌표/대표 사진을 기록 입력 화면에 반영합니다.
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

    // 화면 초기화 후 수정 모드이면 기존 기록을 불러옵니다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        db = TravelDbHelper(this)
        editingId = intent.getLongExtra(FeedFragment.EXTRA_RECORD_ID, 0)
        bindViews()
        dateEdit.text = selectedDate.format(DB_DATE)
        if (editingId > 0) loadRecord(editingId)
    }

    // Activity 종료 시 코루틴과 DB 연결을 정리합니다.
    override fun onDestroy() {
        scope.cancel()
        db.close()
        super.onDestroy()
    }

    // 버튼, 입력창, 사진 목록 Adapter 등 화면 요소를 연결합니다.
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
        editRoot = findViewById(R.id.editRoot)
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
        memoEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollMemoIntoView()
        }
        memoEdit.setOnClickListener { scrollMemoIntoView() }
        configureKeyboardInsets()
        deleteButton.setOnClickListener { confirmDelete() }
        findViewById<TextView>(R.id.editTitle).text = if (editingId > 0) "기록 수정" else "새 기록"
    }

    // 키보드가 올라오면 ScrollView 하단 여백을 늘려 메모 입력칸이 가려지지 않게 합니다.
    private fun configureKeyboardInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(editRoot) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                maxOf(imeBottom, systemBottom) + 24
            )
            if (memoEdit.hasFocus()) {
                view.post { scrollMemoIntoView(delayMillis = 80) }
            }
            insets
        }
    }

    // 메모 입력칸이 포커스될 때 해당 영역으로 스크롤합니다.
    private fun scrollMemoIntoView() {
        scrollMemoIntoView(delayMillis = 250)
    }

    private fun scrollMemoIntoView(delayMillis: Long) {
        editRoot.postDelayed({
            memoEdit.requestRectangleOnScreen(Rect(0, 0, memoEdit.width, memoEdit.height), true)
            editRoot.smoothScrollTo(0, (memoEdit.top - 48).coerceAtLeast(0))
        }, delayMillis)
    }

    /**
     * 수정 모드 시 지정된 여행 기록 ID의 데이터를 데이터베이스에서 조회하여 화면 필드를 채웁니다.
     * 데이터베이스 에러 또는 데이터 누락 상황 발생 시 안전하게 화면을 종료(finish) 처리합니다.
     */
    private fun loadRecord(id: Long) {
        scope.launch {
            val record = withContext(Dispatchers.IO) {
                runCatching { db.getById(id) }.getOrNull()
            } ?: return@launch finish()
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

    // 사진 추가 방법을 갤러리 또는 카메라 중에서 선택하게 합니다.
    private fun choosePhotoSource() {
        AlertDialog.Builder(this)
            .setTitle("사진 추가")
            .setItems(arrayOf("갤러리에서 여러 장 선택", "카메라로 촬영")) { _, which ->
                if (which == 0) {
                    imagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                } else {
                    requestCameraCapture()
                }
            }
            .show()
    }

    // 카메라 권한을 확인한 뒤 촬영을 시작합니다.
    private fun requestCameraCapture() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            startCameraCapture()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    // 촬영 결과를 저장할 파일 URI를 만들고 카메라 앱을 실행합니다.
    private fun startCameraCapture() {
        val imageFile = createCameraFile()
        pendingCameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
        runCatching {
            pendingCameraUri?.let(cameraCapture::launch)
        }.onFailure {
            Toast.makeText(this, "카메라를 실행하지 못했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 사진 목록에 중복 없이 URI를 추가하고 대표 사진이 없으면 첫 사진으로 지정합니다.
    private fun addPhoto(uri: String) {
        if (photoUris.none { it == uri }) {
            photoUris += uri
        }
        if (coverPhotoUri.isNullOrBlank()) {
            coverPhotoUri = uri
        }
        refreshPhotos()
    }

    // 대표 사진 미리보기와 하단 썸네일 목록을 다시 그립니다.
    private fun refreshPhotos() {
        val coverUri = coverPhotoUri ?: photoUris.firstOrNull()
        coverPhotoUri = coverUri
        photoPreview.loadTravelImage(coverUri)
        photoList.visibility = if (photoUris.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        photoAdapter.submitList(photoUris, coverUri)
    }

    // 카메라 촬영 이미지가 저장될 앱 내부 파일을 생성합니다.
    private fun createCameraFile(): File {
        val dir = File(filesDir, "images").apply { mkdirs() }
        return File(dir, "travel_${System.currentTimeMillis()}.jpg")
    }

    // 포토피커 URI 권한 만료를 피하기 위해 선택 이미지를 앱 내부 저장소로 복사합니다.
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

    // 방문 날짜 선택 DatePicker를 표시합니다.
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

    /**
     * 사용자가 입력한 필드값의 유효성을 검사한 뒤 새 기록을 추가하거나 기존 기록을 수정합니다.
     * 저장 도중 데이터베이스 에러가 발생할 경우 알림을 제공하고 화면을 이탈하지 않습니다.
     */
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
            // 지도에서 선택한 좌표가 없으면 사진 EXIF GPS 좌표를 대체로 사용합니다.
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
            
            // 데이터베이스 저장 작업을 안전하게 트라이-캐치하여 오류 시 크래시를 예방합니다.
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    if (editingId > 0) db.update(record) else db.insert(record)
                }.isSuccess
            }
            
            if (success) {
                Toast.makeText(this@EditActivity, "기록을 저장했습니다.", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@EditActivity, "데이터베이스 오류로 기록을 저장할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 수정 모드인 경우 현재 편집 중인 여행 기록을 삭제할지 의사를 묻는 다이얼로그를 표시합니다.
     * 삭제 쿼리 오류 발생 시 사용자에게 피드백을 제공합니다.
     */
    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("기록 삭제")
            .setMessage("이 여행 기록을 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                scope.launch {
                    val success = withContext(Dispatchers.IO) {
                        runCatching { db.delete(editingId) }.isSuccess
                    }
                    if (success) {
                        finish()
                    } else {
                        Toast.makeText(this@EditActivity, "삭제 작업 수행 중 데이터베이스 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    companion object {
        val DB_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
