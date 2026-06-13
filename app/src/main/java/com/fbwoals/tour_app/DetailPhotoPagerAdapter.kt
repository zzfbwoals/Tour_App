package com.fbwoals.tour_app

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class DetailPhotoPagerAdapter(
    photoUris: List<String>
) : RecyclerView.Adapter<DetailPhotoPagerAdapter.PhotoViewHolder>() {
    val photoCount: Int = photoUris.size
    val initialPosition: Int = if (photoUris.size > 1) 1 else 0
    private val pagerPhotoUris: List<String> = when {
        photoUris.isEmpty() -> listOf("")
        photoUris.size == 1 -> photoUris
        else -> listOf(photoUris.last()) + photoUris + listOf(photoUris.first())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detail_photo, parent, false) as ImageView
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val uri = pagerPhotoUris[position].takeIf { it.isNotBlank() }
        holder.bind(uri)
    }

    override fun getItemCount(): Int = pagerPhotoUris.size

    fun realPosition(position: Int): Int {
        if (photoCount <= 1) return 0
        return when (position) {
            0 -> photoCount - 1
            itemCount - 1 -> 0
            else -> position - 1
        }
    }

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
        fun bind(uri: String?) {
            imageView.loadTravelImage(uri)
        }
    }
}
