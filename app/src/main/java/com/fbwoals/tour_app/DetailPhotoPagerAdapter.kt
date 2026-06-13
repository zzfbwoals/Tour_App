package com.fbwoals.tour_app

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class DetailPhotoPagerAdapter(
    private val photoUris: List<String>
) : RecyclerView.Adapter<DetailPhotoPagerAdapter.PhotoViewHolder>() {
    private val effectivePhotoUris = photoUris.ifEmpty { listOf("") }
    val photoCount: Int = photoUris.size
    val initialPosition: Int =
        if (photoUris.size > 1) (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % photoUris.size) else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detail_photo, parent, false) as ImageView
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val uri = effectivePhotoUris[position % effectivePhotoUris.size].takeIf { it.isNotBlank() }
        holder.bind(uri)
    }

    override fun getItemCount(): Int = if (photoUris.size > 1) Int.MAX_VALUE else 1

    fun realPosition(position: Int): Int = if (photoUris.isEmpty()) 0 else position % photoUris.size

    class PhotoViewHolder(
        private val imageView: ImageView
    ) : RecyclerView.ViewHolder(imageView) {
        fun bind(uri: String?) {
            imageView.loadTravelImage(uri)
        }
    }
}
