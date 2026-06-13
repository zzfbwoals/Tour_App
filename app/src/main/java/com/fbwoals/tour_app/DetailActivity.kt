package com.fbwoals.tour_app

import android.content.Intent
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class DetailActivity : AppCompatActivity(), OnMapReadyCallback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var db: TravelDbHelper
    private var record: TravelRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        db = TravelDbHelper(this)
        findViewById<ImageButton>(R.id.detailBackButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.shareButton).setOnClickListener { shareRecord() }
        findViewById<ImageButton>(R.id.detailOptionsButton).setOnClickListener { showOptions(it) }
        val id = intent.getLongExtra(FeedFragment.EXTRA_RECORD_ID, 0)
        loadRecord(id)
    }

    override fun onDestroy() {
        scope.cancel()
        db.close()
        super.onDestroy()
    }

    private fun loadRecord(id: Long) {
        scope.launch {
            record = withContext(Dispatchers.IO) { db.getById(id) }
            val current = record ?: return@launch finish()
            val hasLocation = current.latitude != null && current.longitude != null

            val photoAdapter = DetailPhotoPagerAdapter(current.orderedPhotoUris())
            findViewById<ViewPager2>(R.id.detailPhotoPager).apply {
                adapter = photoAdapter
                setCurrentItem(photoAdapter.initialPosition, false)
                updatePhotoIndicator(photoAdapter.realPosition(photoAdapter.initialPosition), photoAdapter.photoCount)
                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        updatePhotoIndicator(photoAdapter.realPosition(position), photoAdapter.photoCount)
                    }
                })
            }
            findViewById<TextView>(R.id.detailDate).text = current.visitDate
            findViewById<TextView>(R.id.detailPlaceHero).text = current.place
            findViewById<TextView>(R.id.detailMemo).text =
                current.memo.ifBlank { "작성된 메모가 없습니다." }
            findViewById<TextView>(R.id.detailLocationLabel).text =
                if (hasLocation) {
                    withContext(Dispatchers.IO) {
                        resolveLocationLabel(current.latitude!!, current.longitude!!)
                    }
                } else {
                    "위치 정보 없음"
                }

            findViewById<TextView>(R.id.locationMarkerTitle).visibility =
                if (hasLocation) View.VISIBLE else View.GONE
            findViewById<View>(R.id.detailMapContainer).visibility =
                if (hasLocation) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.gpsText).apply {
                visibility = if (hasLocation) View.VISIBLE else View.GONE
                text = if (hasLocation) {
                    "GPS: %.5f, %.5f".format(current.latitude, current.longitude)
                } else {
                    ""
                }
            }

            if (hasLocation) {
                val mapFragment = supportFragmentManager.findFragmentById(R.id.detailGoogleMapContainer)
                    as? SupportMapFragment
                    ?: SupportMapFragment.newInstance().also {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.detailGoogleMapContainer, it)
                            .commitNow()
                    }
                mapFragment.getMapAsync(this@DetailActivity)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        val current = record ?: return
        val position = LatLng(current.latitude ?: return, current.longitude ?: return)
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.addMarker(
            MarkerOptions()
                .position(position)
                .title(current.place)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 14f))
    }

    private fun shareRecord() {
        val current = record ?: return
        val body = "${current.place}\n${current.visitDate}\n\n${current.memo}"
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, body),
                "여행 기록 공유"
            )
        )
    }

    private fun showOptions(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("삭제")
            setOnMenuItemClickListener {
                confirmDelete()
                true
            }
        }.show()
    }

    private fun confirmDelete() {
        val current = record ?: return
        AlertDialog.Builder(this)
            .setTitle("기록 삭제")
            .setMessage("${current.place} 기록을 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) { db.delete(current.no) }
                    finish()
                }
            }
            .show()
    }

    private fun updatePhotoIndicator(selectedIndex: Int, count: Int) {
        val indicator = findViewById<LinearLayout>(R.id.photoIndicator)
        indicator.removeAllViews()
        indicator.visibility = if (count > 1) View.VISIBLE else View.GONE
        repeat(count) { index ->
            indicator.addView(
                TextView(this).apply {
                    text = "●"
                    textSize = if (index == selectedIndex) 12f else 9f
                    alpha = if (index == selectedIndex) 1f else 0.45f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(18, 24)
                }
            )
        }
    }

    private fun resolveLocationLabel(latitude: Double, longitude: Double): String {
        return runCatching {
            val address = Geocoder(this, Locale.KOREA)
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?: return@runCatching "위치 정보 없음"
            listOfNotNull(address.adminArea, address.locality ?: address.subAdminArea)
                .distinct()
                .joinToString(", ")
                .ifBlank { "위치 정보 없음" }
        }.getOrDefault("위치 정보 없음")
    }

    private fun TravelRecord.orderedPhotoUris(): List<String> {
        val coverUri = displayPhotoUri
        val uniquePhotoUris = photoUris.ifEmpty { listOfNotNull(photoUri) }.distinct()
        return if (coverUri == null) {
            uniquePhotoUris
        } else {
            listOf(coverUri) + uniquePhotoUris.filterNot { it == coverUri }
        }
    }
}
