package com.fbwoals.tour_app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
            findViewById<ImageView>(R.id.detailPhoto).loadTravelImage(current.photoUri)
            findViewById<TextView>(R.id.detailDate).text = current.visitDate
            findViewById<TextView>(R.id.detailPlaceHero).text = current.place
            findViewById<TextView>(R.id.detailPlace).text = current.place
            findViewById<TextView>(R.id.detailMemo).text =
                current.memo.ifBlank { "No memo has been written." }

            val hasLocation = current.latitude != null && current.longitude != null
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
                "Share travel record"
            )
        )
    }
}
