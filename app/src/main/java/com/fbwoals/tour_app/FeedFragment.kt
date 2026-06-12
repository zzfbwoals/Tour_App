package com.fbwoals.tour_app

import android.content.Intent
import android.os.Bundle
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fbwoals.tour_app.databinding.FragmentFeedBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DBHelper
    private lateinit var feedAdapter: FeedAdapter
    private var sortByDateDesc = true // 기본값: 최근 날짜순 정렬

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dbHelper = DBHelper(requireContext())

        setupRecyclerView()
        setupMenu()

        // FAB 클릭 시 EditActivity로 이동 (글 작성 추가 모드)
        binding.fabAdd.setOnClickListener {
            val intent = Intent(requireContext(), EditActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadTravelRecords()
    }

    private fun setupRecyclerView() {
        feedAdapter = FeedAdapter(
            onItemClick = { record ->
                // 상세 화면 이동
                val intent = Intent(requireContext(), DetailActivity::class.java).apply {
                    putExtra("RECORD_ID", record.id)
                }
                startActivity(intent)
            },
            onItemLongClick = { record, view ->
                false
            }
        )

        binding.rvFeed.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = feedAdapter
        }
    }

    // 옵션 메뉴 바인딩 (정렬 토글 및 전체 삭제)
    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.option_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_sort -> {
                        sortByDateDesc = !sortByDateDesc
                        val sortText = if (sortByDateDesc) "최근 날짜순" else "오래된 날짜순"
                        Toast.makeText(requireContext(), "정렬 변경: $sortText", Toast.LENGTH_SHORT).show()
                        loadTravelRecords()
                        true
                    }
                    R.id.action_delete_all -> {
                        showDeleteAllDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    // 비동기 코루틴 데이터 로딩
    private fun loadTravelRecords() {
        lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) {
                dbHelper.getAllRecords(sortByDateDesc)
            }
            if (records.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvFeed.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.rvFeed.visibility = View.VISIBLE
                feedAdapter.submitList(records)
            }
        }
    }

    // 전체 삭제 알림창
    private fun showDeleteAllDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("전체 삭제")
            .setMessage("저장된 모든 여행 기록을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        dbHelper.deleteAllRecords()
                    }
                    Toast.makeText(requireContext(), "모든 기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    loadTravelRecords()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 컨텍스트 메뉴 선택 처리
    override fun onContextItemSelected(item: MenuItem): Boolean {
        val selectedRecord = feedAdapter.getContextSelectedRecord() ?: return super.onContextItemSelected(item)
        return when (item.itemId) {
            1 -> { // 수정
                val intent = Intent(requireContext(), EditActivity::class.java).apply {
                    putExtra("RECORD_ID", selectedRecord.id)
                }
                startActivity(intent)
                true
            }
            2 -> { // 삭제
                showDeleteDialog(selectedRecord.id)
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    // 단일 항목 삭제 알림창
    private fun showDeleteDialog(recordId: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("기록 삭제")
            .setMessage("이 여행 기록을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        dbHelper.deleteRecord(recordId)
                    }
                    Toast.makeText(requireContext(), "기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    loadTravelRecords()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
