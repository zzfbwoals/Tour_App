package com.fbwoals.tour_app

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Google Places 자동완성 결과 한 건을 표현합니다.
data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String
)

// 장소 검색 입력 아래에 자동완성 추천 목록을 표시하는 Adapter입니다.
class PlaceSuggestionAdapter(
    private val onSuggestionClick: (PlaceSuggestion) -> Unit
) : RecyclerView.Adapter<PlaceSuggestionAdapter.SuggestionViewHolder>() {
    private val suggestions = mutableListOf<PlaceSuggestion>()

    // 추천 항목 ViewHolder를 생성합니다.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_place_suggestion, parent, false)
        return SuggestionViewHolder(view as ViewGroup, onSuggestionClick)
    }

    // 현재 추천 장소 텍스트를 항목에 바인딩합니다.
    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(suggestions[position])
    }

    override fun getItemCount(): Int = suggestions.size

    // Places API에서 받은 최신 추천 결과로 목록을 갱신합니다.
    fun submitList(newSuggestions: List<PlaceSuggestion>) {
        suggestions.clear()
        suggestions.addAll(newSuggestions)
        notifyDataSetChanged()
    }

    class SuggestionViewHolder(
        private val root: ViewGroup,
        private val onSuggestionClick: (PlaceSuggestion) -> Unit
    ) : RecyclerView.ViewHolder(root) {
        // 추천 장소 이름/주소를 표시하고 클릭 시 선택 콜백을 실행합니다.
        fun bind(suggestion: PlaceSuggestion) {
            root.findViewById<TextView>(R.id.suggestionPrimary).text = suggestion.primaryText
            root.findViewById<TextView>(R.id.suggestionSecondary).text = suggestion.secondaryText
            root.setOnClickListener { onSuggestionClick(suggestion) }
        }
    }
}
