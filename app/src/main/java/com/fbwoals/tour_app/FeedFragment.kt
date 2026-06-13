package com.fbwoals.tour_app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedFragment : Fragment() {
    private lateinit var db: TravelDbHelper
    private lateinit var adapter: TravelAdapter
    private lateinit var emptyView: TextView
    private var newestFirst: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        newestFirst = arguments?.getBoolean(ARG_NEWEST_FIRST, true) ?: true
        db = TravelDbHelper(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        emptyView = view.findViewById(R.id.emptyView)
        adapter = TravelAdapter(
            onOpen = { openDetail(it.no) },
            onEdit = { openEdit(it.no) },
            onDelete = { confirmDelete(it) }
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
            confirmDeleteAll()
        }
        reload()
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }

    fun reload() {
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) { db.getAll(newestFirst) }
            adapter.submitList(records)
            emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openDetail(no: Long) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).putExtra(EXTRA_RECORD_ID, no))
    }

    private fun openEdit(no: Long) {
        startActivity(Intent(requireContext(), EditActivity::class.java).putExtra(EXTRA_RECORD_ID, no))
    }

    private fun confirmDelete(record: TravelRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("기록 삭제")
            .setMessage("${record.place} 기록을 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.delete(record.no) }
                    reload()
                }
            }
            .show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(requireContext())
            .setTitle("전체 삭제")
            .setMessage("저장된 모든 여행 기록을 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.deleteAll() }
                    reload()
                }
            }
            .show()
    }

    companion object {
        const val EXTRA_RECORD_ID = "record_id"
        private const val ARG_NEWEST_FIRST = "newest_first"

        fun newInstance(newestFirst: Boolean): FeedFragment {
            return FeedFragment().apply {
                arguments = Bundle().apply { putBoolean(ARG_NEWEST_FIRST, newestFirst) }
            }
        }
    }
}
