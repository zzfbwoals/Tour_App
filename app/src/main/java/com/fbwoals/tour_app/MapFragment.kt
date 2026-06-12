package com.fbwoals.tour_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.fbwoals.tour_app.databinding.FragmentMapBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DBHelper
    private var googleMap: GoogleMap? = null
    
    private val markerRecordMap = HashMap<Marker, TravelRecord>()
    private var searchMarker: Marker? = null

    // 위치 탐색 권한 요청 Launcher
    private val requestLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableMyLocation()
        } else {
            Toast.makeText(requireContext(), "지도에 내 위치를 표시하려면 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dbHelper = DBHelper(requireContext())

        val mapFragment = childFragmentManager.findFragmentById(R.id.map_main) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()
    }

    private fun setupUI() {
        binding.etMapSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.ivMapSearchGo.setOnClickListener {
            performSearch()
        }

        // 일반/위성 지도 유형 전환 토글
        binding.fabMapType.setOnClickListener {
            googleMap?.let { map ->
                map.mapType = if (map.mapType == GoogleMap.MAP_TYPE_NORMAL) {
                    GoogleMap.MAP_TYPE_SATELLITE
                } else {
                    GoogleMap.MAP_TYPE_NORMAL
                }
                val typeText = if (map.mapType == GoogleMap.MAP_TYPE_NORMAL) "일반 지도" else "위성 지도"
                Toast.makeText(requireContext(), "지도 유형: $typeText", Toast.LENGTH_SHORT).show()
            }
        }

        binding.fabMyLocation.setOnClickListener {
            checkLocationPermissionAndMove()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
        }

        // 지도 위 마커 탭 클릭 시 이벤트
        map.setOnMarkerClickListener { marker ->
            val record = markerRecordMap[marker]
            if (record != null) {
                showMarkerSummary(record)
            } else {
                binding.cardMarkerDetail.visibility = View.GONE
            }
            false
        }

        // 지도 빈 영역 탭 클릭 시 플로팅 카드 닫기
        map.setOnMapClickListener {
            binding.cardMarkerDetail.visibility = View.GONE
        }

        enableMyLocation()
        loadMarkersFromDB()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = true
        } else {
            requestLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun checkLocationPermissionAndMove() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                } else {
                    Toast.makeText(requireContext(), "현재 위치를 잡지 못했습니다. GPS 사용 설정을 확인하세요.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            requestLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // SQLite DB에 등록된 기록들 중 GPS 좌표가 있는 랜드마커 시각화
    private fun loadMarkersFromDB() {
        lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) {
                dbHelper.getAllRecords()
            }
            
            googleMap?.let { map ->
                map.clear()
                markerRecordMap.clear()
                
                var hasMarker = false
                var lastLatLng = LatLng(36.7698, 126.9318) // 기본 마커: 순천향대학교

                records.forEach { record ->
                    if (record.latitude != null && record.longitude != null) {
                        val pos = LatLng(record.latitude, record.longitude)
                        lastLatLng = pos
                        hasMarker = true

                        val marker = map.addMarker(
                            MarkerOptions()
                                .position(pos)
                                .title(record.place)
                                .snippet(record.visitDate)
                        )
                        if (marker != null) {
                            markerRecordMap[marker] = record
                        }
                    }
                }

                // 핀 위치 기반 카메라 줌
                if (hasMarker) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(lastLatLng, 10f))
                } else {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(lastLatLng, 15f))
                }
            }
        }
    }

    // Geocoder 주소 검색 기능 비동기 구현 (가산점 획득 및 ANR 제거)
    private fun performSearch() {
        val keyword = binding.etMapSearch.text.toString().trim()
        if (keyword.isEmpty()) {
            Toast.makeText(requireContext(), "검색할 장소를 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val address = withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(requireContext(), Locale.KOREA)
                    val list = geocoder.getFromLocationName(keyword, 1)
                    if (!list.isNullOrEmpty()) list[0] else null
                } catch (e: Exception) {
                    null
                }
            }

            if (address != null) {
                val searchLatLng = LatLng(address.latitude, address.longitude)
                googleMap?.let { map ->
                    searchMarker?.remove()
                    searchMarker = map.addMarker(
                        MarkerOptions()
                            .position(searchLatLng)
                            .title(keyword)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    )
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(searchLatLng, 15f))
                }
            } else {
                Toast.makeText(requireContext(), "검색 결과가 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 마커 클릭 시 하단 요약 플로팅 카드 노출
    private fun showMarkerSummary(record: TravelRecord) {
        binding.cardMarkerDetail.visibility = View.VISIBLE
        binding.tvMarkerPlace.text = record.place
        binding.tvMarkerDate.text = record.visitDate
        binding.tvMarkerMemo.text = record.memo

        // 비동기 이미지 썸네일 디코딩 로드
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    if (record.photoUri.isNotEmpty()) {
                        val uri = Uri.parse(record.photoUri)
                        context?.contentResolver?.openInputStream(uri)?.use { inputStream ->
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = 4
                            }
                            BitmapFactory.decodeStream(inputStream, null, options)
                        }
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                binding.ivMarkerThumb.setImageBitmap(bitmap)
            } else {
                binding.ivMarkerThumb.setImageResource(R.drawable.ic_gallery)
            }
        }

        binding.btnMarkerGo.setOnClickListener {
            val intent = Intent(requireContext(), DetailActivity::class.java).apply {
                putExtra("RECORD_ID", record.id)
            }
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
