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

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var db: TravelDbHelper
    private lateinit var bottomNavigation: BottomNavigationView
    private var newestFirst = true
    private var currentNavItemId = R.id.nav_feed
    private var syncingNavigation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        db = TravelDbHelper(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

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
                            withContext(Dispatchers.IO) { db.deleteAll() }
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

    override fun onResume() {
        super.onResume()
        refreshCurrent()
    }

    override fun onDestroy() {
        scope.cancel()
        db.close()
        super.onDestroy()
    }

    private fun showFragment(fragment: Fragment) {
        showFragment(fragment, addToBackStack = false)
    }

    private fun showFragment(fragment: Fragment, addToBackStack: Boolean) {
        if (!addToBackStack) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(fragment::class.java.simpleName)
        }
        transaction.commit()
    }

    private fun refreshCurrent() {
        when (val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)) {
            is FeedFragment -> current.reload()
            is MapFragment -> current.reloadMarkers()
        }
    }

    private fun handleBackNavigation() {
        if (currentNavItemId == R.id.nav_map && supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            confirmExit()
        }
    }

    private fun navigateToFeed() {
        if (currentNavItemId != R.id.nav_feed) {
            currentNavItemId = R.id.nav_feed
            bottomNavigation.selectedItemId = R.id.nav_feed
        } else {
            showFragment(FeedFragment.newInstance(newestFirst))
        }
    }

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

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("앱 종료")
            .setMessage("앱을 종료할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("종료") { _, _ -> finish() }
            .show()
    }
}
