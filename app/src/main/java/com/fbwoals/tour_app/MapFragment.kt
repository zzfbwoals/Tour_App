package com.fbwoals.tour_app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// 지도 Fragment로, 저장된 여행 기록 마커와 하단 카드 페이저를 표시합니다.
class MapFragment : Fragment(), OnMapReadyCallback {
    private lateinit var db: TravelDbHelper
    private lateinit var recordAdapter: MapRecordCardAdapter
    private lateinit var suggestionAdapter: PlaceSuggestionAdapter
    private var map: GoogleMap? = null
    private var emptyText: TextView? = null
    private var searchBox: EditText? = null
    private var recordPager: ViewPager2? = null
    private var suggestionList: RecyclerView? = null
    private var cardIndicator: LinearLayout? = null
    private var records: List<TravelRecord> = emptyList()
    private var latestSuggestions: List<PlaceSuggestion> = emptyList()
    private var suppressSuggestionSearch = false

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) enableCurrentLocation()
        }

    // DB Helper를 준비합니다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = TravelDbHelper(requireContext())
    }

    // 지도 화면 레이아웃을 생성합니다.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    // 검색창, 지도 카드 페이저, 자동완성 목록, 지도 버튼을 연결합니다.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initializePlaces()
        emptyText = view.findViewById(R.id.mapEmptyText)
        searchBox = view.findViewById(R.id.mapSearchBox)
        recordPager = view.findViewById(R.id.mapRecordPager)
        suggestionList = view.findViewById(R.id.mapSuggestionList)
        cardIndicator = view.findViewById(R.id.mapCardIndicator)

        recordAdapter = MapRecordCardAdapter { record ->
            startActivity(Intent(requireContext(), DetailActivity::class.java).putExtra(FeedFragment.EXTRA_RECORD_ID, record.no))
        }
        recordPager?.apply {
            adapter = recordAdapter
            offscreenPageLimit = 1
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val realPosition = recordAdapter.realPosition(position)
                    moveToRecord(realPosition)
                    updateCardIndicator(realPosition)
                }

                override fun onPageScrollStateChanged(state: Int) {
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        // 순환용 보조 페이지에 멈췄다면 대응되는 실제 카드로 즉시 보정합니다.
                        recordAdapter.correctedPosition(currentItem)?.let { setCurrentItem(it, false) }
                    }
                }
            })
        }

        suggestionAdapter = PlaceSuggestionAdapter { suggestion -> selectSuggestion(suggestion) }
        suggestionList?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = suggestionAdapter
        }

        searchBox?.apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (suppressSuggestionSearch) return
                    loadPlaceSuggestions(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
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

    // GoogleMap 준비 후 기본 위치, 마커 클릭, 현재 위치 기능을 설정합니다.
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMapToolbarEnabled = true
            uiSettings.isMyLocationButtonEnabled = false
            setOnMapClickListener { hideSuggestions() }
            setOnMarkerClickListener { marker -> onRecordMarkerClick(marker) }
            moveCamera(CameraUpdateFactory.newLatLngZoom(SCH_UNIVERSITY, DEFAULT_ZOOM))
        }
        enableCurrentLocation()
        reloadMarkers()
    }

    // Fragment 종료 시 DB 연결을 닫습니다.
    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }

    // DB에서 GPS가 있는 기록을 읽어 지도 마커와 하단 카드를 갱신합니다.
    /**
     * 데이터베이스에서 저장된 모든 여행 기록 목록을 비동기로 로드하고, 좌표 정보가 유효한 항목들에 대해 마커를 생성합니다.
     * 데이터베이스 입출력 에러 등으로 로드가 실패하는 경우 빈 데이터 목록으로 대체해 안정을 유지합니다.
     */
    fun reloadMarkers() {
        val googleMap = map ?: return
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            records = withContext(Dispatchers.IO) {
                runCatching {
                    db.getAll(true).filter { it.latitude != null && it.longitude != null }
                }.getOrElse { emptyList() }
            }
            googleMap.clear()
            emptyText?.visibility = View.GONE
            recordPager?.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
            cardIndicator?.visibility = if (records.size > 1) View.VISIBLE else View.GONE
            recordAdapter.submitList(records)

            if (records.isEmpty()) {
                addSchMarker(googleMap)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(SCH_UNIVERSITY, DEFAULT_ZOOM))
                return@launch
            }

            records.forEach { record ->
                val position = LatLng(record.latitude!!, record.longitude!!)
                // 저장된 여행지는 Google Maps 기본 마커로 표시합니다.
                googleMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(record.place)
                        .snippet(record.visitDate)
                )?.tag = record.no
            }

            recordPager?.setCurrentItem(recordAdapter.initialPosition, false)
            moveToRecord(0, animate = false)
            updateCardIndicator(0)
        }
    }

    // 마커를 누르면 대응되는 하단 카드 위치로 이동합니다.
    private fun onRecordMarkerClick(marker: Marker): Boolean {
        val recordId = marker.tag as? Long ?: return false
        val index = records.indexOfFirst { it.no == recordId }
        if (index < 0) return false
        recordPager?.setCurrentItem(recordAdapter.pagerPositionForReal(index), true)
        return true
    }

    // 카드 페이지가 바뀔 때 해당 여행 장소로 지도 카메라를 이동합니다.
    private fun moveToRecord(position: Int, animate: Boolean = true) {
        val record = records.getOrNull(position) ?: return
        val target = LatLng(record.latitude ?: return, record.longitude ?: return)
        val update = CameraUpdateFactory.newLatLngZoom(target, 15f)
        if (animate) {
            map?.animateCamera(update)
        } else {
            map?.moveCamera(update)
        }
    }

    // 하단 카드 현재 위치를 점 인디케이터로 표시합니다.
    private fun updateCardIndicator(selectedIndex: Int) {
        val indicator = cardIndicator ?: return
        indicator.removeAllViews()
        val count = recordAdapter.realCount
        indicator.visibility = if (count > 1) View.VISIBLE else View.GONE
        repeat(count) { index ->
            indicator.addView(
                TextView(requireContext()).apply {
                    text = "•"
                    textSize = if (index == selectedIndex) 17f else 12f
                    alpha = if (index == selectedIndex) 1f else 0.45f
                    setTextColor(Color.DKGRAY)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(18, 24)
                }
            )
        }
    }

    /**
     * Google Places SDK 초기화를 위해 앱 메타데이터에서 API 키 정보를 조회합니다.
     * 패키지 정보 조회 시 발생 가능한 NameNotFoundException 예외를 포착하여 안전하게 대응합니다.
     */
    private fun initializePlaces() {
        if (Places.isInitialized()) return
        val apiKey = runCatching {
            val appInfo = requireContext().packageManager.getApplicationInfo(
                requireContext().packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
        }.getOrNull().orEmpty()

        if (apiKey.isBlank()) {
            Toast.makeText(requireContext(), "지도 API 키를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        Places.initialize(requireContext().applicationContext, apiKey)
    }

    // 검색어 입력 중 Google Places 자동완성 결과를 가져옵니다.
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
        Places.createClient(requireContext())
            .findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                if (!isAdded) return@addOnSuccessListener
                latestSuggestions = response.autocompletePredictions.map { prediction ->
                    PlaceSuggestion(
                        placeId = prediction.placeId,
                        primaryText = prediction.getPrimaryText(null).toString(),
                        secondaryText = prediction.getSecondaryText(null).toString()
                    )
                }
                suggestionAdapter.submitList(latestSuggestions)
                suggestionList?.visibility = if (latestSuggestions.isEmpty()) View.GONE else View.VISIBLE
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                latestSuggestions = emptyList()
                suggestionAdapter.submitList(emptyList())
                hideSuggestions()
            }
    }

    // 추천 장소를 선택하면 검색창을 채우고 해당 장소 정보를 조회합니다.
    private fun selectSuggestion(suggestion: PlaceSuggestion) {
        suppressSuggestionSearch = true
        searchBox?.setText(suggestion.primaryText)
        searchBox?.setSelection(suggestion.primaryText.length)
        suppressSuggestionSearch = false
        hideSuggestions()
        hideKeyboard()
        fetchPlace(suggestion.placeId)
    }

    // Place ID로 실제 장소 좌표를 가져와 지도에 임시 마커를 표시합니다.
    private fun fetchPlace(placeId: String) {
        if (!Places.isInitialized()) return
        val fields = listOf(Place.Field.NAME, Place.Field.LAT_LNG)
        val request = FetchPlaceRequest.newInstance(placeId, fields)
        Places.createClient(requireContext())
            .fetchPlace(request)
            .addOnSuccessListener { response ->
                if (!isAdded) return@addOnSuccessListener
                val place = response.place
                val position = place.latLng ?: return@addOnSuccessListener
                map?.apply {
                    addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(place.name)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    )
                    animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
                }
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "장소 정보를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    // 위치 권한이 있으면 GoogleMap의 현재 위치 레이어를 켭니다.
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

    // 마지막으로 알려진 현재 위치로 지도를 이동합니다.
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

    // GPS/네트워크 Provider에서 가장 최신 위치를 찾습니다.
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

    // 키보드 검색 버튼을 눌렀을 때 추천 결과 또는 Geocoder로 장소를 찾습니다.
    private fun searchDestination(query: String) {
        val firstSuggestion = latestSuggestions.firstOrNull()
        if (firstSuggestion != null) {
            selectSuggestion(firstSuggestion)
            return
        }

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

    // 저장된 기록이 없을 때 기본 위치인 순천향대학교 마커를 표시합니다.
    private fun addSchMarker(googleMap: GoogleMap) {
        googleMap.addMarker(
            MarkerOptions()
                .position(SCH_UNIVERSITY)
                .title("순천향대학교")
                .snippet("기본 지도 위치")
        )
    }

    // 자동완성 목록을 숨깁니다.
    private fun hideSuggestions() {
        suggestionList?.visibility = View.GONE
    }

    // 검색창 키보드를 숨기고 포커스를 해제합니다.
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
