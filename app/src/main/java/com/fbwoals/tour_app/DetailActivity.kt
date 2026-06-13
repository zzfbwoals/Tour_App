package com.fbwoals.tour_app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.resume

// 여행 기록 상세 화면으로, 사진 슬라이드/지도/공유/삭제 기능을 제공합니다.
class DetailActivity : AppCompatActivity(), OnMapReadyCallback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var db: TravelDbHelper
    private var record: TravelRecord? = null
    private var detailMap: GoogleMap? = null

    // 상세 화면 버튼을 연결하고 전달받은 기록 ID를 로드합니다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        db = TravelDbHelper(this)
        findViewById<ImageButton>(R.id.detailBackButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.shareButton).setOnClickListener { shareRecordImage() }
        findViewById<ImageButton>(R.id.detailOptionsButton).setOnClickListener { showOptions(it) }
        loadRecord(intent.getLongExtra(FeedFragment.EXTRA_RECORD_ID, 0))
    }

    // Activity 종료 시 코루틴과 DB 연결을 정리합니다.
    override fun onDestroy() {
        scope.cancel()
        db.close()
        super.onDestroy()
    }

    // DB에서 기록을 읽어 사진, 메모, 위치, 지도 화면을 채웁니다.
    /**
     * 지정한 ID에 해당하는 여행 기록 데이터를 데이터베이스에서 비동기로 조회하여 화면에 표시합니다.
     * 데이터베이스 에러가 발생하더라도 앱이 종료되지 않도록 예외를 안전하게 처리합니다.
     */
    private fun loadRecord(id: Long) {
        scope.launch {
            record = withContext(Dispatchers.IO) {
                runCatching { db.getById(id) }.getOrNull()
            }
            val current = record ?: return@launch finish()
            val hasLocation = current.latitude != null && current.longitude != null

            val photoAdapter = DetailPhotoPagerAdapter(current.orderedPhotoUris())
            findViewById<ViewPager2>(R.id.detailPhotoPager).apply {
                adapter = photoAdapter
                excludeSystemBackGesture()
                // 대표 사진을 먼저 보여주고, 여러 장이면 순환 슬라이드를 준비합니다.
                setCurrentItem(photoAdapter.initialPosition, false)
                updatePhotoIndicator(photoAdapter.realPosition(photoAdapter.initialPosition), photoAdapter.photoCount)
                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        updatePhotoIndicator(photoAdapter.realPosition(position), photoAdapter.photoCount)
                    }

                    override fun onPageScrollStateChanged(state: Int) {
                        if (state == ViewPager2.SCROLL_STATE_IDLE) {
                            photoAdapter.correctedPosition(currentItem)?.let { setCurrentItem(it, false) }
                        }
                    }
                })
            }

            findViewById<TextView>(R.id.detailDate).text = current.visitDate
            findViewById<TextView>(R.id.detailPlaceHero).text = current.place
            findViewById<TextView>(R.id.detailMemo).text =
                current.memo.ifBlank { "작성된 메모가 없습니다." }
            findViewById<TextView>(R.id.detailLocationLabel).text =
                if (hasLocation) {
                    withContext(Dispatchers.IO) { resolveLocationLabel(current.latitude!!, current.longitude!!) }
                } else {
                    "위치 정보 없음"
                }

            findViewById<TextView>(R.id.locationMarkerTitle).visibility =
                if (hasLocation) View.VISIBLE else View.GONE
            findViewById<View>(R.id.detailMapContainer).visibility =
                if (hasLocation) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.gpsText).apply {
                visibility = if (hasLocation) View.VISIBLE else View.GONE
                text = if (hasLocation) "GPS: %.5f, %.5f".format(current.latitude, current.longitude) else ""
            }

            if (hasLocation) {
                // 위치가 있는 기록만 GoogleMap Fragment를 생성합니다.
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

    // 상세 지도 준비 후 기록 위치에 마커를 표시합니다.
    override fun onMapReady(googleMap: GoogleMap) {
        detailMap = googleMap
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

    /**
     * 메인 화면 전체(상세 정보 및 지도 합성본)를 이미지화하여 공유 인텐트를 발송합니다.
     * 스레드 접근 위반(CalledFromWrongThreadException)을 예방하기 위해 드로잉과 파일 저장을 분리 처리합니다.
     */
    private fun shareRecordImage() {
        val root = findViewById<View>(R.id.detailRoot)
        if (root.width == 0 || root.height == 0) return

        scope.launch {
            // Google 지도 스냅샷은 지도 SDK 내부 비동기 동작을 대기하여 획득합니다.
            val mapSnapshot = captureMapSnapshotIfVisible()
            
            // 메인 UI 스레드에서 화면 View 구성요소를 안전하게 비트맵으로 렌더링하고 합성합니다.
            val compositeBitmap = runCatching {
                generateCompositeBitmap(root, mapSnapshot)
            }.getOrNull()

            if (compositeBitmap == null) {
                Toast.makeText(this@DetailActivity, "화면 공유 이미지를 만들지 못했습니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 파일 출력 및 FileProvider URI 획득 작업은 백그라운드 IO 스레드에서 실행합니다.
            val uri = withContext(Dispatchers.IO) {
                runCatching { saveBitmapToCache(compositeBitmap) }.getOrNull()
            }

            if (uri == null) {
                Toast.makeText(this@DetailActivity, "화면 공유 이미지를 만들지 못했습니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 공유 인텐트 전송 시 앱 부재 또는 권한 미승인 등의 오류 상황에 대응합니다.
            runCatching {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("image/png")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "여행 기록 이미지 공유"
                    )
                )
            }.onFailure {
                Toast.makeText(this@DetailActivity, "공유를 진행할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * GoogleMap은 일반 뷰 계층 드로잉에 포함되지 않으므로, 지도 SDK 자체 기능으로 스냅샷을 획득합니다.
     * 취득은 비동기적 콜백으로 동작하며 코루틴 대기를 이용해 처리합니다.
     */
    private suspend fun captureMapSnapshotIfVisible(): Bitmap? {
        val mapContainer = findViewById<View>(R.id.detailMapContainer)
        val googleMap = detailMap ?: return null
        if (mapContainer.visibility != View.VISIBLE || mapContainer.width == 0 || mapContainer.height == 0) return null
        return suspendCancellableCoroutine { continuation ->
            googleMap.snapshot { bitmap -> continuation.resume(bitmap) }
        }
    }

    /**
     * 메인 화면 뷰(상세 피드) 내용 위에 구글맵 스냅샷을 알맞은 위치에 배치하여 병합된 비트맵을 생성합니다.
     * UI 스레드에서 뷰 상태가 안전할 때 호출되어야 합니다.
     */
    private fun generateCompositeBitmap(view: View, mapSnapshot: Bitmap?): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        mapSnapshot?.let { drawMapSnapshot(canvas, view, it) }
        return bitmap
    }

    /**
     * 합성 완료된 비트맵 데이터를 캐시 경로에 파일로 저장하고, FileProvider를 통해 안전한 공유용 Uri를 반환합니다.
     * 디스크 입출력을 포함하므로 IO 스레드(Dispatchers.IO) 환경에서 호출합니다.
     */
    private fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val dir = File(cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "travel_record_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output -> 
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) 
        }
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    // 지도 스냅샷을 실제 지도 View 위치에 맞춰 캔버스에 덧그립니다.
    private fun drawMapSnapshot(canvas: Canvas, root: View, mapSnapshot: Bitmap) {
        val mapContainer = findViewById<View>(R.id.detailMapContainer)
        val rootLocation = IntArray(2)
        val mapLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        mapContainer.getLocationOnScreen(mapLocation)
        val left = mapLocation[0] - rootLocation[0]
        val top = mapLocation[1] - rootLocation[1]
        canvas.drawBitmap(mapSnapshot, null, Rect(left, top, left + mapContainer.width, top + mapContainer.height), null)
    }

    // 상세 화면 옵션 메뉴를 띄웁니다.
    private fun showOptions(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("삭제")
            setOnMenuItemClickListener {
                confirmDelete()
                true
            }
        }.show()
    }

    /**
     * 사용자에게 삭제를 한 번 더 재확인하는 모달을 출력하고, 승인 시 데이터베이스에서 해당 기록을 제거합니다.
     * 데이터베이스 에러로 인한 삭제 롤백 및 실패 예외에 대응하여 Toast를 출력합니다.
     */
    private fun confirmDelete() {
        val current = record ?: return
        AlertDialog.Builder(this)
            .setTitle("기록 삭제")
            .setMessage("${current.place} 기록을 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                scope.launch {
                    val success = withContext(Dispatchers.IO) {
                        runCatching { db.delete(current.no) }.isSuccess
                    }
                    if (!success) {
                        Toast.makeText(this@DetailActivity, "기록 삭제 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }
            }
            .show()
    }

    // 사진 개수와 현재 위치를 점 인디케이터로 표시합니다.
    private fun updatePhotoIndicator(selectedIndex: Int, count: Int) {
        val indicator = findViewById<LinearLayout>(R.id.photoIndicator)
        indicator.removeAllViews()
        indicator.visibility = if (count > 1) View.VISIBLE else View.GONE
        repeat(count) { index ->
            indicator.addView(
                TextView(this).apply {
                    text = "•"
                    textSize = if (index == selectedIndex) 18f else 13f
                    alpha = if (index == selectedIndex) 1f else 0.55f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(20, 28)
                }
            )
        }
    }

    // 사진 슬라이드 영역에서 시스템 뒤로가기 제스처가 먼저 먹지 않도록 제외 영역을 지정합니다.
    private fun View.excludeSystemBackGesture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        post {
            systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }
    }

    // 위도/경도를 시/도, 시/군/구 수준의 한글 위치 라벨로 변환합니다.
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

    // 대표 사진을 첫 번째로 배치해 상세 사진 목록 표시 순서를 만듭니다.
    private fun TravelRecord.orderedPhotoUris(): List<String> {
        val coverUri = displayPhotoUri
        val uniquePhotoUris = photoUris.ifEmpty { listOfNotNull(photoUri) }.distinct()
        return if (coverUri == null) uniquePhotoUris else listOf(coverUri) + uniquePhotoUris.filterNot { it == coverUri }
    }
}
