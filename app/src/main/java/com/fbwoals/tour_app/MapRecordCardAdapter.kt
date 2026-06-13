package com.fbwoals.tour_app

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MapRecordCardAdapter(
    private val onRecordClick: (TravelRecord) -> Unit
) : RecyclerView.Adapter<MapRecordCardAdapter.RecordViewHolder>() {
    private val records = mutableListOf<TravelRecord>()
    private val pagerRecords = mutableListOf<TravelRecord>()

    val realCount: Int
        get() = records.size

    val initialPosition: Int
        get() = if (records.size > 1) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_map_record_card, parent, false)
        return RecordViewHolder(view as ViewGroup, onRecordClick)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(pagerRecords[position])
    }

    override fun getItemCount(): Int = pagerRecords.size

    fun submitList(newRecords: List<TravelRecord>) {
        records.clear()
        records.addAll(newRecords)
        pagerRecords.clear()
        when {
            newRecords.isEmpty() -> Unit
            newRecords.size == 1 -> pagerRecords.addAll(newRecords)
            else -> {
                pagerRecords.add(newRecords.last())
                pagerRecords.addAll(newRecords)
                pagerRecords.add(newRecords.first())
            }
        }
        notifyDataSetChanged()
    }

    fun realPosition(position: Int): Int {
        if (records.size <= 1) return 0
        return when (position) {
            0 -> records.lastIndex
            itemCount - 1 -> 0
            else -> position - 1
        }
    }

    fun pagerPositionForReal(realPosition: Int): Int {
        return if (records.size > 1) realPosition + 1 else realPosition
    }

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
