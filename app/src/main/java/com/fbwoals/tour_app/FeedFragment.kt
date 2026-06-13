package com.fbwoals.tour_app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 여행 기록 목록 Fragment로, RecyclerView 목록과 선택/삭제 모드를 관리합니다.
class FeedFragment : Fragment() {
    private lateinit var db: TravelDbHelper
    private lateinit var adapter: TravelAdapter
    private lateinit var emptyView: TextView
    private lateinit var modeText: TextView
    private lateinit var subtitle: TextView
    private var newestFirst: Boolean = true

    // 정렬 상태와 DB Helper를 초기화합니다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        newestFirst = arguments?.getBoolean(ARG_NEWEST_FIRST, true) ?: true
        db = TravelDbHelper(requireContext())
    }

    // 기록 목록 화면 레이아웃을 생성합니다.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    // RecyclerView, 정렬/삭제 버튼, Adapter 이벤트를 연결합니다.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        emptyView = view.findViewById(R.id.emptyView)
        modeText = view.findViewById(R.id.feedModeText)
        subtitle = view.findViewById(R.id.feedSubtitle)
        adapter = TravelAdapter(
            onOpen = { openDetail(it.no) },
            onEdit = { openEdit(it.no) },
            onDelete = { confirmDelete(it) },
            onSelectionChanged = { updateSelectionText(it) }
        )
        view.findViewById<RecyclerView>(R.id.travelRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FeedFragment.adapter
        }
        view.findViewById<View>(R.id.sortButton).setOnClickListener {
            newestFirst = !newestFirst
            reload()
        }
        view.findViewById<View>(R.id.deleteAllButton).setOnClickListener {
            if (adapter.selectionMode) {
                confirmDeleteSelected()
            } else {
                confirmDeleteAll()
            }
        }
        view.findViewById<View>(R.id.selectDeleteButton).setOnClickListener {
            adapter.setSelectionMode(!adapter.selectionMode)
            updateSelectionText(adapter.selectedRecordIds().size)
        }
        reload()
    }

    // Fragment 종료 시 DB 연결을 닫습니다.
    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }

    // DB에서 최신 목록을 비동기로 읽어 RecyclerView에 표시합니다.
    /**
     * 데이터베이스에서 최신 여행 기록 목록을 비동기 조회하여 어댑터에 갱신 처리합니다.
     * 데이터베이스 파일 유실, 권한 거부 등의 오류 발생 시 빈 리스트로 방어 대처합니다.
     */
    fun reload() {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) {
                runCatching { db.getAll(newestFirst) }.getOrElse { emptyList() }
            }
            adapter.submitList(records)
            emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // MainActivity 옵션 메뉴에서 선택 삭제 모드를 토글할 때 호출됩니다.
    fun toggleSelectionMode() {
        adapter.setSelectionMode(!adapter.selectionMode)
        updateSelectionText(adapter.selectedRecordIds().size)
    }

    // 선택한 기록의 상세 화면을 엽니다.
    private fun openDetail(no: Long) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).putExtra(EXTRA_RECORD_ID, no))
    }

    // 선택한 기록의 수정 화면을 엽니다.
    private fun openEdit(no: Long) {
        startActivity(Intent(requireContext(), EditActivity::class.java).putExtra(EXTRA_RECORD_ID, no))
    }

    // 선택 삭제 모드 상태와 선택 개수를 상단 문구에 반영합니다.
    private fun updateSelectionText(selectedCount: Int) {
        if (adapter.selectionMode) {
            modeText.text = "선택 삭제"
            subtitle.text = "선택된 기록 ${selectedCount}개"
        } else {
            modeText.text = "나의 여정"
            subtitle.text = "최근 여행 기록"
        }
    }

    // 개별 기록 삭제 전 확인창을 띄웁니다.
    /**
     * 특정 단일 기록 삭제를 요청하기 전 모달 다이얼로그를 표시합니다.
     * 데이터베이스 오류로 인해 삭제에 실패한 경우 사용자 피드백을 안내합니다.
     */
    private fun confirmDelete(record: TravelRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("기록 삭제")
            .setMessage("${record.place} 기록을 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        runCatching { db.delete(record.no) }.isSuccess
                    }
                    if (!success) {
                        Toast.makeText(requireContext(), "기록 삭제 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                    reload()
                }
            }
            .show()
    }

    // 선택된 여러 기록을 삭제하기 전 확인창을 띄웁니다.
    /**
     * 체크된 복수 개의 기록 목록을 일괄 삭제합니다.
     * 내부 루프 중 데이터베이스 예외 발생 시 에러 메시지를 띄웁니다.
     */
    private fun confirmDeleteSelected() {
        val ids = adapter.selectedRecordIds()
        if (ids.isEmpty()) {
            Toast.makeText(requireContext(), "삭제할 기록을 선택하세요.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("선택 삭제")
            .setMessage("선택한 ${ids.size}개의 여행 기록을 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        runCatching { ids.forEach(db::delete) }.isSuccess
                    }
                    if (!success) {
                        Toast.makeText(requireContext(), "기록 삭제 중 일부 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                    adapter.setSelectionMode(false)
                    reload()
                }
            }
            .show()
    }

    // 전체 여행 기록 삭제 전 확인창을 띄웁니다.
    /**
     * 전체 여행 기록 데이터베이스 비우기를 시도합니다.
     * 데이터베이스 에러 발생 상황 시 팝업 문구로 인지시킵니다.
     */
    private fun confirmDeleteAll() {
        AlertDialog.Builder(requireContext())
            .setTitle("전체 삭제")
            .setMessage("저장된 모든 여행 기록을 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        runCatching { db.deleteAll() }.isSuccess
                    }
                    if (!success) {
                        Toast.makeText(requireContext(), "전체 삭제 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                    reload()
                }
            }
            .show()
    }

    companion object {
        const val EXTRA_RECORD_ID = "record_id"
        private const val ARG_NEWEST_FIRST = "newest_first"

        // 정렬 상태를 arguments로 전달해 Fragment를 생성합니다.
        fun newInstance(newestFirst: Boolean): FeedFragment {
            return FeedFragment().apply {
                arguments = Bundle().apply { putBoolean(ARG_NEWEST_FIRST, newestFirst) }
            }
        }
    }
}
