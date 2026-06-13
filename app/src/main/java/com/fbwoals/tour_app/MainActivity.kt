package com.fbwoals.tour_app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 앱의 시작 Activity로, 하단 탭 전환과 전체 옵션 메뉴를 관리합니다.
class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var db: TravelDbHelper
    private lateinit var bottomNavigation: BottomNavigationView
    private var newestFirst = true
    private var currentNavItemId = R.id.nav_feed
    private var syncingNavigation = false

    // 초기 화면, BottomNavigation, 뒤로가기 백스택 처리를 설정합니다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        db = TravelDbHelper(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 엣지 투 엣지 화면에서 시스템 바 영역만큼 루트 패딩을 적용합니다.
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setSupportActionBar(null)
        findViewById<FloatingActionButton>(R.id.addRecordFab).setOnClickListener {
            startActivity(Intent(this, EditActivity::class.java))
        }
        supportFragmentManager.addOnBackStackChangedListener {
            syncNavigationWithCurrentFragment()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
        bottomNavigation = findViewById(R.id.bottomNavigation)
        bottomNavigation.setOnItemSelectedListener {
            if (it.itemId == currentNavItemId) return@setOnItemSelectedListener true
            currentNavItemId = it.itemId
            when (it.itemId) {
                R.id.nav_feed -> {
                    showFragment(FeedFragment.newInstance(newestFirst), addToBackStack = false)
                    true
                }
                R.id.nav_map -> {
                    showFragment(MapFragment(), addToBackStack = true)
                    true
                }
                else -> false
            }
        }
        if (savedInstanceState == null) {
            currentNavItemId = R.id.nav_feed
            showFragment(FeedFragment.newInstance(newestFirst), addToBackStack = false)
        }
    }

    // 상단 옵션 메뉴를 생성합니다.
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    // 정렬, 전체 삭제, 선택 삭제 메뉴 동작을 처리합니다.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort -> {
                newestFirst = !newestFirst
                navigateToFeed()
                true
            }
            R.id.action_delete_all -> {
                AlertDialog.Builder(this)
                    .setTitle("전체 삭제")
                    .setMessage("저장된 모든 여행 기록을 삭제할까요?")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("삭제") { _, _ ->
                        scope.launch {
                            // 데이터베이스의 모든 테이블 자료 삭제 도중 발생할 수 있는 쿼리 예외를 포착합니다.
                            val success = withContext(Dispatchers.IO) {
                                runCatching { db.deleteAll() }.isSuccess
                            }
                            if (!success) {
                                android.widget.Toast.makeText(this@MainActivity, "전체 삭제 중 오류가 발생했습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            refreshCurrent()
                        }
                    }
                    .show()
                true
            }
            R.id.action_select_delete -> {
                val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                if (current is FeedFragment) {
                    current.toggleSelectionMode()
                } else {
                    navigateToFeed()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // 다른 Activity에서 돌아왔을 때 현재 탭 내용을 최신 DB 상태로 갱신합니다.
    override fun onResume() {
        super.onResume()
        refreshCurrent()
    }

    // Activity 종료 시 코루틴과 DB 연결을 정리합니다.
    override fun onDestroy() {
        scope.cancel()
        db.close()
        super.onDestroy()
    }

    // 기본 Fragment 교체 진입점입니다.
    private fun showFragment(fragment: Fragment) {
        showFragment(fragment, addToBackStack = false)
    }

    // 지도 탭은 addToBackStack으로 관리하고, 기록 탭 이동 시 백스택을 정리합니다.
    private fun showFragment(fragment: Fragment, addToBackStack: Boolean) {
        if (!addToBackStack) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
        if (addToBackStack) {
            // 과제 요구사항의 Fragment 백스택 관리를 명시적으로 만족하는 부분입니다.
            transaction.addToBackStack(fragment::class.java.simpleName)
        }
        transaction.commit()
    }

    // 현재 표시 중인 Fragment만 새로고침합니다.
    private fun refreshCurrent() {
        when (val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)) {
            is FeedFragment -> current.reload()
            is MapFragment -> current.reloadMarkers()
        }
    }

    // 지도 탭에서는 백스택을 pop하고, 기록 탭에서는 앱 종료 확인창을 띄웁니다.
    private fun handleBackNavigation() {
        if (currentNavItemId == R.id.nav_map && supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            confirmExit()
        }
    }

    // 옵션 메뉴에서 기록 탭으로 돌아가야 할 때 사용하는 공통 함수입니다.
    private fun navigateToFeed() {
        if (currentNavItemId != R.id.nav_feed) {
            currentNavItemId = R.id.nav_feed
            bottomNavigation.selectedItemId = R.id.nav_feed
        } else {
            showFragment(FeedFragment.newInstance(newestFirst))
        }
    }

    // Fragment 백스택 변화에 맞춰 BottomNavigation 선택 상태를 동기화합니다.
    private fun syncNavigationWithCurrentFragment() {
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        val targetItemId = when (current) {
            is MapFragment -> R.id.nav_map
            else -> R.id.nav_feed
        }
        if (currentNavItemId == targetItemId || syncingNavigation) return
        syncingNavigation = true
        currentNavItemId = targetItemId
        bottomNavigation.selectedItemId = targetItemId
        syncingNavigation = false
    }

    // 앱 종료 전 사용자 확인을 받습니다.
    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("앱 종료")
            .setMessage("앱을 종료할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("종료") { _, _ -> finish() }
            .show()
    }
}
