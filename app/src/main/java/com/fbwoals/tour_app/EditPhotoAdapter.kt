package com.fbwoals.tour_app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EditPhotoAdapter(
    private val onCoverSelected: (String) -> Unit
) : RecyclerView.Adapter<EditPhotoAdapter.PhotoViewHolder>() {
    private val photoUris = mutableListOf<String>()
    private var coverPhotoUri: String? = null

    fun submitList(nextPhotoUris: List<String>, nextCoverPhotoUri: String?) {
        photoUris.clear()
        photoUris.addAll(nextPhotoUris)
        coverPhotoUri = nextCoverPhotoUri ?: nextPhotoUris.firstOrNull()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_edit_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photoUris[position])
    }

    override fun getItemCount(): Int = photoUris.size

    inner class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumbnail: ImageView = view.findViewById(R.id.photoThumb)
        private val coverBadge: TextView = view.findViewById(R.id.coverBadge)

        fun bind(uri: String) {
            thumbnail.loadTravelImage(uri)
            coverBadge.visibility = if (uri == coverPhotoUri) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onCoverSelected(uri) }
        }
    }
}
