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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_map_record_card, parent, false)
        return RecordViewHolder(view as ViewGroup, onRecordClick)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount(): Int = records.size

    fun submitList(newRecords: List<TravelRecord>) {
        records.clear()
        records.addAll(newRecords)
        notifyDataSetChanged()
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
