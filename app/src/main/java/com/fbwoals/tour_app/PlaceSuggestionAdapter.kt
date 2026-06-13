package com.fbwoals.tour_app

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String
)

class PlaceSuggestionAdapter(
    private val onSuggestionClick: (PlaceSuggestion) -> Unit
) : RecyclerView.Adapter<PlaceSuggestionAdapter.SuggestionViewHolder>() {
    private val suggestions = mutableListOf<PlaceSuggestion>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_place_suggestion, parent, false)
        return SuggestionViewHolder(view as ViewGroup, onSuggestionClick)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(suggestions[position])
    }

    override fun getItemCount(): Int = suggestions.size

    fun submitList(newSuggestions: List<PlaceSuggestion>) {
        suggestions.clear()
        suggestions.addAll(newSuggestions)
        notifyDataSetChanged()
    }

    class SuggestionViewHolder(
        private val root: ViewGroup,
        private val onSuggestionClick: (PlaceSuggestion) -> Unit
    ) : RecyclerView.ViewHolder(root) {
        fun bind(suggestion: PlaceSuggestion) {
            root.findViewById<TextView>(R.id.suggestionPrimary).text = suggestion.primaryText
            root.findViewById<TextView>(R.id.suggestionSecondary).text = suggestion.secondaryText
            root.setOnClickListener { onSuggestionClick(suggestion) }
        }
    }
}
