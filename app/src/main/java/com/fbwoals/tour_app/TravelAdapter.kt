package com.fbwoals.tour_app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

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

    fun submitList(next: List<TravelRecord>) {
        records.clear()
        records.addAll(next)
        selectedIds.retainAll(next.map { it.no }.toSet())
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun selectedRecordIds(): List<Long> = selectedIds.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_travel_record, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount(): Int = records.size

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

        fun bind(record: TravelRecord) {
            place.text = record.place
            date.text = record.visitDate
            memo.text = record.memo.ifBlank { "남겨진 메모가 없습니다." }
            thumbnail.loadTravelImage(record.displayPhotoUri)
            checkBox.visibility = if (selectionMode) View.VISIBLE else View.GONE
            checkBox.isChecked = selectedIds.contains(record.no)
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
