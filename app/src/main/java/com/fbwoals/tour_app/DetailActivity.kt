package com.fbwoals.tour_app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.fbwoals.tour_app.databinding.ActivityDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var dbHelper: DBHelper
    private var recordId: Int = -1
    private var googleMap: GoogleMap? = null
    
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var placeName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dbHelper = DBHelper(this)

        setSupportActionBar(binding.toolbarDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarDetail.setNavigationOnClickListener { finish() }

        recordId = intent.getIntExtra("RECORD_ID", -1)
        if (recordId == -1) {
            Toast.makeText(this, "올바르지 않은 접근 방식입니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadRecordDetails()
    }

    private fun loadRecordDetails() {
        lifecycleScope.launch {
            val record = withContext(Dispatchers.IO) {
                dbHelper.getRecordById(recordId)
            }

            if (record != null) {
                placeName = record.place
                binding.tvDetailPlace.text = record.place
                binding.tvDetailDate.text = record.visitDate
                binding.tvDetailMemo.text = record.memo

                if (record.photoUri.isNotEmpty()) {
                    loadPhoto(record.photoUri)
                } else {
                    binding.ivDetailPhoto.setImageResource(R.drawable.ic_gallery)
                }

                // 사진 GPS 데이터 존재 시 하단 미니맵 연동
                if (record.latitude != null && record.longitude != null) {
                    latitude = record.latitude
                    longitude = record.longitude
                    binding.layoutDetailMap.visibility = View.VISIBLE
                    binding.tvDetailGpsText.text = String.format("GPS: %.5f, %.5f", record.latitude, record.longitude)

                    val mapFragment = supportFragmentManager
                        .findFragmentById(R.id.map_detail_mini) as SupportMapFragment
                    mapFragment.getMapAsync(this@DetailActivity)

                    binding.btnOpenExternalMap.setOnClickListener {
                        openExternalMap(record.latitude, record.longitude, record.place)
                    }
                } else {
                    binding.layoutDetailMap.visibility = View.GONE
                }
            } else {
                Toast.makeText(this@DetailActivity, "기록을 읽어오지 못했습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadPhoto(uriString: String) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(uriString)
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 1
                        }
                        BitmapFactory.decodeStream(inputStream, null, options)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                binding.ivDetailPhoto.setImageBitmap(bitmap)
            } else {
                binding.ivDetailPhoto.setImageResource(R.drawable.ic_gallery)
            }
        }
    }

    // 미니 구글 맵 초기화 콜백
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val lat = latitude ?: return
        val lng = longitude ?: return
        val location = LatLng(lat, lng)

        // 미니맵은 단독 뷰잉이므로 터치/줌 컨트롤을 배제
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isMapToolbarEnabled = false
            isScrollGesturesEnabled = false
            isZoomGesturesEnabled = false
        }

        map.addMarker(
            MarkerOptions()
                .position(location)
                .title(placeName)
        )
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
    }

    // 암시적 인텐트(Implicit Intent) 기반 외부 구글맵 연동
    private fun openExternalMap(lat: Double, lng: Double, label: String) {
        val gmmIntentUri = Uri.parse("geo:$lat,$lng?q=" + Uri.encode(label))
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
            )
            startActivity(webIntent)
        }
    }
}
