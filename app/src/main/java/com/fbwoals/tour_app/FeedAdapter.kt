package com.fbwoals.tour_app

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fbwoals.tour_app.databinding.ItemTripCardBinding
import kotlinx.coroutines.*

class FeedAdapter(
    private val onItemClick: (TravelRecord) -> Unit,
    private val onItemLongClick: (TravelRecord, View) -> Boolean
) : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {

    private val recordList = mutableListOf<TravelRecord>()
    private var contextSelectedRecord: TravelRecord? = null

    fun submitList(list: List<TravelRecord>) {
        recordList.clear()
        recordList.addAll(list)
        notifyDataSetChanged()
    }

    fun getContextSelectedRecord(): TravelRecord? = contextSelectedRecord

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val binding = ItemTripCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FeedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        holder.bind(recordList[position])
    }

    override fun getItemCount(): Int = recordList.size

    inner class FeedViewHolder(private val binding: ItemTripCardBinding) : 
        RecyclerView.ViewHolder(binding.root), View.OnCreateContextMenuListener {

        private var loadJob: Job? = null
        private var currentRecord: TravelRecord? = null

        init {
            binding.root.setOnClickListener {
                currentRecord?.let { onItemClick(it) }
            }
            binding.root.setOnLongClickListener {
                contextSelectedRecord = currentRecord
                false
            }
            binding.ivMore.setOnClickListener {
                contextSelectedRecord = currentRecord
                binding.root.showContextMenu()
            }
            binding.root.setOnCreateContextMenuListener(this)
        }

        fun bind(record: TravelRecord) {
            currentRecord = record
            binding.tvPlace.text = record.place
            binding.tvDate.text = record.visitDate
            binding.tvMemo.text = record.memo

            loadThumbnail(record.photoUri)
        }

        // 비동기 이미지 로딩 (+2점 가산점 및 ANR 방지)
        private fun loadThumbnail(uriString: String) {
            loadJob?.cancel()
            if (uriString.isEmpty()) {
                binding.ivThumbnail.setImageResource(R.drawable.ic_gallery)
                return
            }
            
            loadJob = CoroutineScope(Dispatchers.Main).launch {
                binding.ivThumbnail.setImageResource(0)
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        val uri = Uri.parse(uriString)
                        val inputStream = itemView.context.contentResolver.openInputStream(uri)
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 4 // 메모리 보호용 리사이징
                        }
                        BitmapFactory.decodeStream(inputStream, null, options)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    binding.ivThumbnail.setImageBitmap(bitmap)
                } else {
                    binding.ivThumbnail.setImageResource(R.drawable.ic_gallery)
                }
            }
        }

        override fun onCreateContextMenu(
            menu: ContextMenu?,
            v: View?,
            menuInfo: ContextMenu.ContextMenuInfo?
        ) {
            menu?.setHeaderTitle("선택한 기록 관리")
            menu?.add(0, 1, 0, "수정")
            menu?.add(0, 2, 1, "삭제")
        }
    }
}
