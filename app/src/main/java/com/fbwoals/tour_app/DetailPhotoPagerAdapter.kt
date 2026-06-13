package com.fbwoals.tour_app

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

// 상세 화면의 사진 ViewPager2를 구성하고, 끝에서 처음으로 이어지는 순환 효과를 제공합니다.
class DetailPhotoPagerAdapter(
    photoUris: List<String>
) : RecyclerView.Adapter<DetailPhotoPagerAdapter.PhotoViewHolder>() {
    val photoCount: Int = photoUris.size
    val initialPosition: Int = if (photoUris.size > 1) 1 else 0
    private val pagerPhotoUris: List<String> = when {
        photoUris.isEmpty() -> listOf("")
        photoUris.size == 1 -> photoUris
        // 양끝에 보조 페이지를 추가해 마지막 다음이 첫 사진처럼 보이게 만듭니다.
        else -> listOf(photoUris.last()) + photoUris + listOf(photoUris.first())
    }

    // 사진 한 장을 표시할 ImageView ViewHolder를 생성합니다.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detail_photo, parent, false) as ImageView
        return PhotoViewHolder(view)
    }

    // 현재 페이지 위치에 맞는 사진 URI를 ViewHolder에 바인딩합니다.
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val uri = pagerPhotoUris[position].takeIf { it.isNotBlank() }
        holder.bind(uri)
    }

    override fun getItemCount(): Int = pagerPhotoUris.size

    // 보조 페이지 위치를 실제 사진 인덱스로 변환합니다.
    fun realPosition(position: Int): Int {
        if (photoCount <= 1) return 0
        return when (position) {
            0 -> photoCount - 1
            itemCount - 1 -> 0
            else -> position - 1
        }
    }

    // 보조 페이지에 도달했을 때 실제 페이지로 즉시 보정할 위치를 반환합니다.
    fun correctedPosition(position: Int): Int? {
        if (photoCount <= 1) return null
        return when (position) {
            0 -> photoCount
            itemCount - 1 -> 1
            else -> null
        }
    }

    class PhotoViewHolder(
        private val imageView: ImageView
    ) : RecyclerView.ViewHolder(imageView) {
        // 권한이 끊긴 사진 URI도 안전하게 기본 이미지로 대체됩니다.
        fun bind(uri: String?) {
            imageView.loadTravelImage(uri)
        }
    }
}
