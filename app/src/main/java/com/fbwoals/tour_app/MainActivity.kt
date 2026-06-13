package com.fbwoals.tour_app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
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
    private var newestFirst = true

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
        findViewById<BottomNavigationView>(R.id.bottomNavigation).setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_feed -> {
                    showFragment(FeedFragment.newInstance(newestFirst))
                    true
                }
                R.id.nav_map -> {
                    showFragment(MapFragment())
                    true
                }
                else -> false
            }
        }
        if (savedInstanceState == null) {
            showFragment(FeedFragment.newInstance(newestFirst))
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
                showFragment(FeedFragment.newInstance(newestFirst))
                true
            }
            R.id.action_delete_all -> {
                AlertDialog.Builder(this)
                    .setTitle("?„ì²´ ?? œ")
                    .setMessage("?€?¥ëœ ëª¨ë“  ?¬í–‰ ê¸°ë¡???? œ? ê¹Œ??")
                    .setNegativeButton("ì·¨ì†Œ", null)
                    .setPositiveButton("?? œ") { _, _ ->
                        scope.launch {
                            withContext(Dispatchers.IO) { db.deleteAll() }
                            refreshCurrent()
                        }
                    }
                    .show()
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
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun refreshCurrent() {
        when (val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)) {
            is FeedFragment -> current.reload()
            is MapFragment -> current.reloadMarkers()
        }
    }
}
