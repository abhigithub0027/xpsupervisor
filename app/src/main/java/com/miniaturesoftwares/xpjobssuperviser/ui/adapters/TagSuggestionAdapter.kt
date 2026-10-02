package com.miniaturesoftwares.xpjobssuperviser.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.network.TaskTag

/**
 * Live work-type suggestions shown under the search box.
 *
 * Results come from the server already filtered, so this does no matching of
 * its own - it just renders what came back, in the order it came back.
 */
class TagSuggestionAdapter(
    private val onPick: (TaskTag) -> Unit,
) : RecyclerView.Adapter<TagSuggestionAdapter.ViewHolder>() {

    private var items: List<TaskTag> = emptyList()

    fun submit(tags: List<TaskTag>) {
        items = tags
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tag_suggestion, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvTagTitle)
        private val subtitle: TextView = view.findViewById(R.id.tvTagSubtitle)

        fun bind(tag: TaskTag) {
            title.text = tag.workType

            // category / subcategory are what distinguish two similarly-named
            // work types, so show them when the server sends them.
            val detail = listOfNotNull(
                tag.category?.takeIf { it.isNotBlank() },
                tag.subcategory?.takeIf { it.isNotBlank() },
            ).joinToString(" • ")
            subtitle.text = detail
            subtitle.visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE

            itemView.setOnClickListener { onPick(tag) }
        }
    }
}
