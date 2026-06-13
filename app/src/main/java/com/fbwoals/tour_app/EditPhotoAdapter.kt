package com.fbwoals.tour_app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// 기록 추가/수정 화면에서 여러 사진 썸네일과 대표 사진 선택 상태를 표시합니다.
class EditPhotoAdapter(
    private val onCoverSelected: (String) -> Unit
) : RecyclerView.Adapter<EditPhotoAdapter.PhotoViewHolder>() {
    private val photoUris = mutableListOf<String>()
    private var coverPhotoUri: String? = null

    // 새 사진 목록과 대표 사진 URI를 반영해 썸네일 목록을 갱신합니다.
    fun submitList(nextPhotoUris: List<String>, nextCoverPhotoUri: String?) {
        photoUris.clear()
        photoUris.addAll(nextPhotoUris)
        coverPhotoUri = nextCoverPhotoUri ?: nextPhotoUris.firstOrNull()
        notifyDataSetChanged()
    }

    // 썸네일 항목 ViewHolder를 생성합니다.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_edit_photo, parent, false)
        return PhotoViewHolder(view)
    }

    // 위치에 맞는 사진 URI를 썸네일에 연결합니다.
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photoUris[position])
    }

    override fun getItemCount(): Int = photoUris.size

    inner class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumbnail: ImageView = view.findViewById(R.id.photoThumb)
        private val coverBadge: TextView = view.findViewById(R.id.coverBadge)

        // 대표 사진이면 배지를 보여주고, 클릭 시 대표 사진으로 지정합니다.
        fun bind(uri: String) {
            thumbnail.loadTravelImage(uri)
            coverBadge.visibility = if (uri == coverPhotoUri) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onCoverSelected(uri) }
        }
    }
}
