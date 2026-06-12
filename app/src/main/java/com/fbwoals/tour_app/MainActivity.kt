package com.fbwoals.tour_app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.fbwoals.tour_app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar 설정 (옵션 메뉴 활용을 위함)
        setSupportActionBar(binding.toolbar)

        // 초기 화면 설정 (FeedFragment)
        if (savedInstanceState == null) {
            replaceFragment(FeedFragment(), "feed")
        }

        // BottomNavigationView 선택 이벤트 처리
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_feed -> {
                    replaceFragment(FeedFragment(), "feed")
                    true
                }
                R.id.nav_map -> {
                    replaceFragment(MapFragment(), "map")
                    true
                }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
    }
}