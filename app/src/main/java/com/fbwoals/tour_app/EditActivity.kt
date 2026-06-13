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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    private var editingId: Long = 0
    private var photoUriText: String? = null
    private var selectedDate: LocalDate = LocalDate.now()
    private var pendingCameraUri: Uri? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            photoUriText = uri.toString()
            photoPreview.loadTravelImage(photoUriText)
        }
    }

    private val cameraCapture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUriText = pendingCameraUri?.toString()
            photoPreview.loadTravelImage(photoUriText)
        }
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
        placeEdit = findViewById(R.id.placeEdit)
        dateEdit = findViewById(R.id.dateEdit)
        memoEdit = findViewById(R.id.memoEdit)
        photoPreview = findViewById(R.id.photoPreview)
        deleteButton = findViewById(R.id.deleteButton)
        dateEdit.setOnClickListener { showDatePicker() }
        deleteButton.setOnClickListener { confirmDelete() }
        findViewById<TextView>(R.id.editTitle).text = if (editingId > 0) "Edit Record" else "New Record"
    }

    private fun loadRecord(id: Long) {
        scope.launch {
            val record = withContext(Dispatchers.IO) { db.getById(id) } ?: return@launch finish()
            placeEdit.setText(record.place)
            memoEdit.setText(record.memo)
            selectedDate = runCatching { LocalDate.parse(record.visitDate, DB_DATE) }.getOrDefault(LocalDate.now())
            dateEdit.text = selectedDate.format(DB_DATE)
            photoUriText = record.photoUri
            photoPreview.loadTravelImage(photoUriText)
            deleteButton.visibility = android.view.View.VISIBLE
        }
    }

    private fun choosePhotoSource() {
        AlertDialog.Builder(this)
            .setTitle("?¬ì§„ ì²¨ë?")
            .setItems(arrayOf("ê°¤ëŸ¬ë¦¬ì—??? íƒ", "ì¹´ë©”?¼ë¡œ ì´¬ì˜")) { _, which ->
                if (which == 0) {
                    imagePicker.launch(arrayOf("image/*"))
                } else {
                    val imageFile = createCameraFile()
                    pendingCameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
                    pendingCameraUri?.let(cameraCapture::launch)
                }
            }
            .show()
    }

    private fun createCameraFile(): File {
        val dir = File(filesDir, "images").apply { mkdirs() }
        return File(dir, "travel_${System.currentTimeMillis()}.jpg")
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
            placeEdit.error = "?¬í–‰ì§€ ?´ë¦„???…ë ¥?˜ì„¸??"
            return
        }
        val memo = memoEdit.text.toString().trim()
        scope.launch {
            val location = withContext(Dispatchers.IO) { ExifLocationReader.readLocation(this@EditActivity, photoUriText) }
            val record = TravelRecord(
                no = editingId,
                place = place,
                visitDate = selectedDate.format(DB_DATE),
                memo = memo,
                photoUri = photoUriText,
                latitude = location?.first,
                longitude = location?.second
            )
            withContext(Dispatchers.IO) {
                if (editingId > 0) db.update(record) else db.insert(record)
            }
            Toast.makeText(this@EditActivity, "ê¸°ë¡???€?¥í–ˆ?µë‹ˆ??", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("ê¸°ë¡ ?? œ")
            .setMessage("???¬í–‰ ê¸°ë¡???? œ? ê¹Œ??")
            .setNegativeButton("ì·¨ì†Œ", null)
            .setPositiveButton("?? œ") { _, _ ->
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
