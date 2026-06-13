package com.fbwoals.tour_app

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// 지도 화면 하단 카드 ViewPager2에 여행 기록을 표시하고 순환 슬라이드를 지원합니다.
class MapRecordCardAdapter(
    private val onRecordClick: (TravelRecord) -> Unit
) : RecyclerView.Adapter<MapRecordCardAdapter.RecordViewHolder>() {
    private val records = mutableListOf<TravelRecord>()
    private val pagerRecords = mutableListOf<TravelRecord>()

    val realCount: Int
        get() = records.size

    val initialPosition: Int
        get() = if (records.size > 1) 1 else 0

    // 지도 카드 레이아웃을 ViewHolder로 생성합니다.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_map_record_card, parent, false)
        return RecordViewHolder(view as ViewGroup, onRecordClick)
    }

    // 보조 페이지까지 포함된 pagerRecords의 데이터를 카드에 바인딩합니다.
    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(pagerRecords[position])
    }

    override fun getItemCount(): Int = pagerRecords.size

    // 실제 기록 목록을 저장하고, 순환을 위해 양끝 보조 카드를 추가합니다.
    fun submitList(newRecords: List<TravelRecord>) {
        records.clear()
        records.addAll(newRecords)
        pagerRecords.clear()
        when {
            newRecords.isEmpty() -> Unit
            newRecords.size == 1 -> pagerRecords.addAll(newRecords)
            else -> {
                // [마지막, 실제 목록..., 첫번째] 구조로 만들어 자연스러운 순환을 구현합니다.
                pagerRecords.add(newRecords.last())
                pagerRecords.addAll(newRecords)
                pagerRecords.add(newRecords.first())
            }
        }
        notifyDataSetChanged()
    }

    // ViewPager 위치를 실제 records 인덱스로 변환합니다.
    fun realPosition(position: Int): Int {
        if (records.size <= 1) return 0
        return when (position) {
            0 -> records.lastIndex
            itemCount - 1 -> 0
            else -> position - 1
        }
    }

    // 마커 클릭처럼 실제 인덱스에서 ViewPager 위치로 이동해야 할 때 사용합니다.
    fun pagerPositionForReal(realPosition: Int): Int {
        return if (records.size > 1) realPosition + 1 else realPosition
    }

    // 보조 카드에 멈췄을 때 대응되는 실제 카드 위치를 반환합니다.
    fun correctedPosition(position: Int): Int? {
        if (records.size <= 1) return null
        return when (position) {
            0 -> records.size
            itemCount - 1 -> 1
            else -> null
        }
    }

    class RecordViewHolder(
        private val root: ViewGroup,
        private val onRecordClick: (TravelRecord) -> Unit
    ) : RecyclerView.ViewHolder(root) {
        // 카드에 대표 사진, 장소명, 날짜, 메모를 표시하고 클릭 시 상세 화면으로 이동합니다.
        fun bind(record: TravelRecord) {
            root.findViewById<ImageView>(R.id.mapCardImage).loadTravelImage(record.displayPhotoUri)
            root.findViewById<TextView>(R.id.mapCardTitle).text = record.place
            root.findViewById<TextView>(R.id.mapCardDate).text = record.visitDate
            root.findViewById<TextView>(R.id.mapCardMemo).text =
                record.memo.ifBlank { "작성된 메모가 없습니다." }
            root.setOnClickListener { onRecordClick(record) }
        }
    }
}
