package com.fbwoals.tour_app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// 여행 기록 RecyclerView의 목록 표시, 클릭, 길게 누르기 메뉴, 선택 삭제 모드를 담당합니다.
class TravelAdapter(
    private val onOpen: (TravelRecord) -> Unit,
    private val onEdit: (TravelRecord) -> Unit,
    private val onDelete: (TravelRecord) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<TravelAdapter.RecordViewHolder>() {

    private val records = mutableListOf<TravelRecord>()
    private val selectedIds = mutableSetOf<Long>()
    var selectionMode: Boolean = false
        private set

    // DB에서 읽은 최신 기록 목록을 Adapter에 반영합니다.
    fun submitList(next: List<TravelRecord>) {
        records.clear()
        records.addAll(next)
        selectedIds.retainAll(next.map { it.no }.toSet())
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    // 선택 삭제 모드를 켜거나 끄고 선택 상태를 갱신합니다.
    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    // 선택 삭제 대상인 기록 번호 목록을 반환합니다.
    fun selectedRecordIds(): List<Long> = selectedIds.toList()

    // 여행 기록 카드 ViewHolder를 생성합니다.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_travel_record, parent, false)
        return RecordViewHolder(view)
    }

    // 현재 위치의 여행 기록을 카드에 표시합니다.
    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount(): Int = records.size

    // 선택 삭제 모드에서 한 기록의 선택 상태를 반전합니다.
    private fun toggleSelection(record: TravelRecord) {
        if (!selectedIds.add(record.no)) {
            selectedIds.remove(record.no)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    inner class RecordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumbnail: ImageView = view.findViewById(R.id.thumbnailImage)
        private val place: TextView = view.findViewById(R.id.placeText)
        private val date: TextView = view.findViewById(R.id.dateText)
        private val memo: TextView = view.findViewById(R.id.memoText)
        private val checkBox: CheckBox = view.findViewById(R.id.selectCheckBox)

        // 카드 UI에 장소명, 날짜, 메모, 대표 사진을 표시하고 클릭 이벤트를 연결합니다.
        fun bind(record: TravelRecord) {
            place.text = record.place
            date.text = record.visitDate
            memo.text = record.memo.ifBlank { "남겨진 메모가 없습니다." }
            thumbnail.loadTravelImage(record.displayPhotoUri)
            checkBox.visibility = if (selectionMode) View.VISIBLE else View.GONE
            checkBox.isChecked = selectedIds.contains(record.no)
            // 선택 모드에서는 카드 클릭이 상세 이동 대신 선택 토글로 동작합니다.
            checkBox.setOnClickListener { toggleSelection(record) }
            itemView.setOnClickListener {
                if (selectionMode) toggleSelection(record) else onOpen(record)
            }
            itemView.setOnLongClickListener {
                if (selectionMode) {
                    toggleSelection(record)
                } else {
                    showMenu(record)
                }
                true
            }
        }

        // 목록 항목 길게 누르기 컨텍스트 메뉴에서 수정/삭제를 제공합니다.
        private fun showMenu(record: TravelRecord) {
            PopupMenu(itemView.context, itemView).apply {
                inflate(R.menu.item_context_menu)
                setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.action_edit -> onEdit(record)
                        R.id.action_delete -> onDelete(record)
                    }
                    true
                }
            }.show()
        }
    }
}
