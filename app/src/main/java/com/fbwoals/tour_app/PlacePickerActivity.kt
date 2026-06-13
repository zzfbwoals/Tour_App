package com.fbwoals.tour_app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileOutputStream

// 기록 작성 중 지도에서 장소를 검색/선택하는 Activity입니다.
class PlacePickerActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var searchBox: EditText
    private lateinit var selectedPanel: View
    private lateinit var selectedName: TextView
    private lateinit var selectedAddress: TextView
    private lateinit var suggestionList: RecyclerView
    private lateinit var suggestionAdapter: PlaceSuggestionAdapter
    private var map: GoogleMap? = null
    private var pendingSelection: PickedPlace? = null
    private var latestSuggestions: List<PlaceSuggestion> = emptyList()
    private var suppressSuggestionSearch = false

    // 현재 위치 버튼에서 위치 권한을 요청한 뒤 지도를 현재 위치로 이동합니다.
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableCurrentLocation()
                moveToCurrentLocation()
            }
        }

    // 검색 UI와 지도 Fragment를 초기화합니다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_place_picker)
        initializePlaces()

        findViewById<ImageButton>(R.id.placePickerBackButton).setOnClickListener { finish() }
        searchBox = findViewById(R.id.placePickerSearchBox)
        selectedPanel = findViewById(R.id.selectedPlacePanel)
        selectedName = findViewById(R.id.selectedPlaceName)
        selectedAddress = findViewById(R.id.selectedPlaceAddress)
        suggestionList = findViewById(R.id.placePickerSuggestionList)
        suggestionAdapter = PlaceSuggestionAdapter { suggestion -> selectSuggestion(suggestion) }
        suggestionList.layoutManager = LinearLayoutManager(this)
        suggestionList.adapter = suggestionAdapter
        findViewById<ImageButton>(R.id.placePickerMyLocationButton).setOnClickListener {
            moveToCurrentLocation()
        }
        findViewById<ImageButton>(R.id.placePickerZoomInButton).setOnClickListener {
            map?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        findViewById<ImageButton>(R.id.placePickerZoomOutButton).setOnClickListener {
            map?.animateCamera(CameraUpdateFactory.zoomOut())
        }
        findViewById<MaterialButton>(R.id.confirmPlaceButton).setOnClickListener { confirmSelection() }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressSuggestionSearch) return
                loadPlaceSuggestions(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        searchBox.setOnEditorActionListener { _, actionId, event ->
            val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
            if (isSearchAction || isEnter) {
                searchPlace(searchBox.text.toString())
                hideKeyboard()
                true
            } else {
                false
            }
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.placePickerMapContainer)
            as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.placePickerMapContainer, it)
                    .commitNow()
            }
        mapFragment.getMapAsync(this)
    }

    // GoogleMap 준비 후 기본 위치, POI 클릭, 현재 위치 레이어를 설정합니다.
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMapToolbarEnabled = true
            moveCamera(CameraUpdateFactory.newLatLngZoom(SCH_UNIVERSITY, DEFAULT_ZOOM))
            setOnMapClickListener { hideSuggestions() }
            setOnPoiClickListener { poi ->
                hideSuggestions()
                fetchPlace(poi.placeId)
            }
        }
        enableCurrentLocation()
    }

    /**
     * Google Places SDK 초기화를 위해 앱 메타데이터에서 API 키 정보를 조회합니다.
     * 패키지 정보 조회 시 발생 가능한 NameNotFoundException 예외를 포착하여 안전하게 대응합니다.
     */
    private fun initializePlaces() {
        if (Places.isInitialized()) return
        val apiKey = runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            appInfo.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
        }.getOrNull().orEmpty()

        if (apiKey.isBlank()) {
            Toast.makeText(this, "지도 API 키를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        Places.initialize(applicationContext, apiKey)
    }

    // 입력한 글자에 맞는 장소 자동완성 목록을 가져옵니다.
    private fun loadPlaceSuggestions(query: String) {
        val keyword = query.trim()
        if (keyword.length < 2) {
            latestSuggestions = emptyList()
            suggestionAdapter.submitList(emptyList())
            hideSuggestions()
            return
        }
        if (!Places.isInitialized()) return

        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(keyword)
            .build()
        Places.createClient(this)
            .findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                latestSuggestions = response.autocompletePredictions.map { prediction ->
                    PlaceSuggestion(
                        placeId = prediction.placeId,
                        primaryText = prediction.getPrimaryText(null).toString(),
                        secondaryText = prediction.getSecondaryText(null).toString()
                    )
                }
                suggestionAdapter.submitList(latestSuggestions)
                suggestionList.visibility = if (latestSuggestions.isEmpty()) View.GONE else View.VISIBLE
            }
            .addOnFailureListener {
                latestSuggestions = emptyList()
                suggestionAdapter.submitList(emptyList())
                hideSuggestions()
            }
    }

    // 추천 장소를 누르면 검색창에 반영하고 상세 장소 정보를 조회합니다.
    private fun selectSuggestion(suggestion: PlaceSuggestion) {
        suppressSuggestionSearch = true
        searchBox.setText(suggestion.primaryText)
        searchBox.setSelection(suggestion.primaryText.length)
        suppressSuggestionSearch = false
        hideSuggestions()
        hideKeyboard()
        fetchPlace(suggestion.placeId)
    }

    // 키보드 검색 버튼을 눌렀을 때 첫 추천 장소 또는 Places 검색 결과를 선택합니다.
    private fun searchPlace(query: String) {
        val firstSuggestion = latestSuggestions.firstOrNull()
        if (firstSuggestion != null) {
            selectSuggestion(firstSuggestion)
            return
        }

        val keyword = query.trim()
        if (keyword.isBlank()) return
        if (!Places.isInitialized()) {
            Toast.makeText(this, "장소 검색을 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(keyword)
            .build()
        Places.createClient(this)
            .findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val placeId = response.autocompletePredictions.firstOrNull()?.placeId
                if (placeId == null) {
                    Toast.makeText(this, "일치하는 장소를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    fetchPlace(placeId)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "장소 검색에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    // Place ID로 이름, 주소, 좌표, 대표 사진 메타데이터를 가져옵니다.
    private fun fetchPlace(placeId: String) {
        if (!Places.isInitialized()) return
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.PHOTO_METADATAS
        )
        val request = FetchPlaceRequest.newInstance(placeId, fields)
        Places.createClient(this)
            .fetchPlace(request)
            .addOnSuccessListener { response -> showSelection(response.place) }
            .addOnFailureListener {
                Toast.makeText(this, "장소 정보를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    // 선택된 장소를 패널에 표시하고 지도 마커를 이동합니다.
    private fun showSelection(place: Place) {
        val position = place.latLng ?: return
        val name = place.name.orEmpty()
        val address = place.address.orEmpty()
        pendingSelection = PickedPlace(
            name = name,
            address = address,
            latitude = position.latitude,
            longitude = position.longitude,
            photoMetadata = place.photoMetadatas?.firstOrNull()
        )
        selectedName.text = name
        selectedAddress.text = address.ifBlank { "주소 정보가 없습니다." }
        selectedPanel.visibility = View.VISIBLE
        map?.apply {
            clear()
            addMarker(
                MarkerOptions()
                    .position(position)
                    .title(name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            animateCamera(CameraUpdateFactory.newLatLngZoom(position, 16f))
        }
    }

    // 선택 확정 시 대표 사진을 가져온 뒤 EditActivity에 결과를 반환합니다.
    private fun confirmSelection() {
        val selection = pendingSelection ?: return
        val metadata = selection.photoMetadata
        if (metadata == null || !Places.isInitialized()) {
            finishWithSelection(selection, null)
            return
        }

        val request = FetchPhotoRequest.builder(metadata)
            .setMaxWidth(1200)
            .setMaxHeight(900)
            .build()
        Places.createClient(this)
            .fetchPhoto(request)
            .addOnSuccessListener { response ->
                finishWithSelection(selection, savePlacePhoto(response.bitmap))
            }
            .addOnFailureListener {
                finishWithSelection(selection, null)
            }
    }

    // 위치 권한이 있으면 현재 위치 레이어를 켭니다.
    private fun enableCurrentLocation() {
        val googleMap = map ?: return
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            return
        }

        try {
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = false
        } catch (_: SecurityException) {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 마지막으로 알려진 현재 위치로 지도를 이동합니다.
    private fun moveToCurrentLocation() {
        val googleMap = map ?: return
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        val location = getLastKnownLocation()
        if (location == null) {
            Toast.makeText(this, "현재 위치를 아직 확인할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                16f
            )
        )
    }

    // GPS/네트워크 Provider에서 가장 최신 위치를 반환합니다.
    private fun getLastKnownLocation(): Location? {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.mapNotNull { provider ->
            runCatching {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    locationManager.getLastKnownLocation(provider)
                } else {
                    null
                }
            }.getOrNull()
        }.maxByOrNull { it.time }
    }

    // Places 대표 사진 Bitmap을 앱 내부 파일로 저장하고 URI를 반환합니다.
    private fun savePlacePhoto(bitmap: Bitmap): String? {
        return runCatching {
            val dir = File(filesDir, "images").apply { mkdirs() }
            val file = File(dir, "place_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    // 선택 장소 정보를 Intent에 담아 이전 화면으로 전달합니다.
    private fun finishWithSelection(selection: PickedPlace, photoUri: String?) {
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_PLACE_NAME, selection.name)
                .putExtra(EXTRA_PLACE_LATITUDE, selection.latitude)
                .putExtra(EXTRA_PLACE_LONGITUDE, selection.longitude)
                .putExtra(EXTRA_PLACE_PHOTO_URI, photoUri)
        )
        finish()
    }

    // 자동완성 목록을 숨깁니다.
    private fun hideSuggestions() {
        suggestionList.visibility = View.GONE
    }

    // 검색창 키보드를 숨기고 포커스를 해제합니다.
    private fun hideKeyboard() {
        val inputManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(searchBox.windowToken, 0)
        searchBox.clearFocus()
    }

    private data class PickedPlace(
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        val photoMetadata: PhotoMetadata?
    )

    companion object {
        const val EXTRA_PLACE_NAME = "place_name"
        const val EXTRA_PLACE_LATITUDE = "place_latitude"
        const val EXTRA_PLACE_LONGITUDE = "place_longitude"
        const val EXTRA_PLACE_PHOTO_URI = "place_photo_uri"
        private val SCH_UNIVERSITY = LatLng(36.76974, 126.93152)
        private const val DEFAULT_ZOOM = 16f
    }
}
