package com.fbwoals.tour_app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TravelAdapter(
    private val onOpen: (TravelRecord) -> Unit,
    private val onEdit: (TravelRecord) -> Unit,
    private val onDelete: (TravelRecord) -> Unit
) : RecyclerView.Adapter<TravelAdapter.RecordViewHolder>() {

    private val records = mutableListOf<TravelRecord>()

    fun submitList(next: List<TravelRecord>) {
        records.clear()
        records.addAll(next)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_travel_record, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount(): Int = records.size

    inner class RecordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumbnail: ImageView = view.findViewById(R.id.thumbnailImage)
        private val place: TextView = view.findViewById(R.id.placeText)
        private val date: TextView = view.findViewById(R.id.dateText)
        private val memo: TextView = view.findViewById(R.id.memoText)

        fun bind(record: TravelRecord) {
            place.text = record.place
            date.text = record.visitDate
            memo.text = record.memo.ifBlank { "남겨진 메모가 없습니다." }
            thumbnail.loadTravelImage(record.displayPhotoUri)
            itemView.setOnClickListener { onOpen(record) }
            itemView.setOnLongClickListener {
                showMenu(record)
                true
            }
        }

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
