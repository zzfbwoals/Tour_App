package com.fbwoals.tour_app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MapFragment : Fragment(), OnMapReadyCallback {
    private lateinit var db: TravelDbHelper
    private var map: GoogleMap? = null
    private var emptyText: TextView? = null
    private var searchBox: EditText? = null
    private var recordCard: View? = null
    private var recordsById: Map<Long, TravelRecord> = emptyMap()

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) enableCurrentLocation()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = TravelDbHelper(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        emptyText = view.findViewById(R.id.mapEmptyText)
        recordCard = view.findViewById(R.id.mapRecordCard)
        view.findViewById<ImageButton>(R.id.mapCardCloseButton).setOnClickListener {
            recordCard?.visibility = View.GONE
        }
        searchBox = view.findViewById<EditText>(R.id.mapSearchBox).apply {
            setOnEditorActionListener { _, actionId, event ->
                val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
                val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
                if (isSearchAction || isEnter) {
                    searchDestination(text.toString())
                    hideKeyboard()
                    true
                } else {
                    false
                }
            }
        }
        view.findViewById<ImageButton>(R.id.zoomInButton).setOnClickListener {
            map?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        view.findViewById<ImageButton>(R.id.zoomOutButton).setOnClickListener {
            map?.animateCamera(CameraUpdateFactory.zoomOut())
        }
        view.findViewById<ImageButton>(R.id.myLocationButton).setOnClickListener {
            moveToCurrentLocation()
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.googleMapContainer)
            as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction()
                    .replace(R.id.googleMapContainer, it)
                    .commitNow()
            }
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMapToolbarEnabled = true
            uiSettings.isMyLocationButtonEnabled = false
            setOnMapClickListener { recordCard?.visibility = View.GONE }
            setOnMarkerClickListener { marker -> onRecordMarkerClick(marker) }
            moveCamera(CameraUpdateFactory.newLatLngZoom(SCH_UNIVERSITY, DEFAULT_ZOOM))
        }
        enableCurrentLocation()
        reloadMarkers()
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }

    fun reloadMarkers() {
        val googleMap = map ?: return
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) {
                db.getAll(true).filter { it.latitude != null && it.longitude != null }
            }
            recordsById = records.associateBy { it.no }
            googleMap.clear()
            recordCard?.visibility = View.GONE
            emptyText?.visibility = View.GONE

            if (records.isEmpty()) {
                addSchMarker(googleMap)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(SCH_UNIVERSITY, DEFAULT_ZOOM))
                return@launch
            }

            val icon = createTravelMarkerIcon()
            records.forEach { record ->
                val position = LatLng(record.latitude!!, record.longitude!!)
                googleMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(record.place)
                        .snippet(record.visitDate)
                        .icon(icon)
                )?.tag = record.no
            }
            val first = records.first()
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude!!, first.longitude!!), 10f))
        }
    }

    private fun onRecordMarkerClick(marker: Marker): Boolean {
        val recordId = marker.tag as? Long ?: return false
        val record = recordsById[recordId] ?: return false
        showRecordCard(record)
        map?.animateCamera(CameraUpdateFactory.newLatLng(marker.position))
        return true
    }

    private fun showRecordCard(record: TravelRecord) {
        val card = recordCard ?: return
        card.findViewById<ImageView>(R.id.mapCardImage).loadTravelImage(record.displayPhotoUri)
        card.findViewById<TextView>(R.id.mapCardTitle).text = record.place
        card.findViewById<TextView>(R.id.mapCardDate).text = record.visitDate
        card.findViewById<TextView>(R.id.mapCardMemo).text =
            record.memo.ifBlank { "작성된 메모가 없습니다." }
        card.setOnClickListener {
            startActivity(Intent(requireContext(), DetailActivity::class.java).putExtra(FeedFragment.EXTRA_RECORD_ID, record.no))
        }
        card.visibility = View.VISIBLE
    }

    private fun createTravelMarkerIcon() =
        BitmapDescriptorFactory.fromBitmap(
            Bitmap.createScaledBitmap(
                BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_custom),
                72,
                72,
                true
            )
        )

    private fun enableCurrentLocation() {
        val googleMap = map ?: return
        val hasFineLocation = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        try {
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = false
        } catch (_: SecurityException) {
            Toast.makeText(requireContext(), "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun moveToCurrentLocation() {
        val googleMap = map ?: return
        val hasFineLocation = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        val location = getLastKnownLocation()
        if (location == null) {
            Toast.makeText(requireContext(), "현재 위치를 아직 확인할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                16f
            )
        )
    }

    private fun getLastKnownLocation(): Location? {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.mapNotNull { provider ->
            runCatching {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    locationManager.getLastKnownLocation(provider)
                } else {
                    null
                }
            }.getOrNull()
        }.maxByOrNull { it.time }
    }

    private fun searchDestination(query: String) {
        val googleMap = map ?: return
        val keyword = query.trim()
        if (keyword.isBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Geocoder(requireContext(), Locale.KOREA)
                        .getFromLocationName(keyword, 1)
                        ?.firstOrNull()
                }.getOrNull()
            }

            if (result == null) {
                Toast.makeText(requireContext(), "일치하는 장소를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val position = LatLng(result.latitude, result.longitude)
            googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(keyword)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
        }
    }

    private fun addSchMarker(googleMap: GoogleMap) {
        googleMap.addMarker(
            MarkerOptions()
                .position(SCH_UNIVERSITY)
                .title("순천향대학교")
                .snippet("기본 지도 위치")
                .icon(createTravelMarkerIcon())
        )
    }

    private fun hideKeyboard() {
        val view = searchBox ?: return
        val inputManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    companion object {
        private val SCH_UNIVERSITY = LatLng(36.76974, 126.93152)
        private const val DEFAULT_ZOOM = 16f
    }
}
