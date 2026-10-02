package com.miniaturesoftwares.xpjobssuperviser.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.network.SampleVideo

/** Full-screen "View All" grid of sample videos: bigger thumbnail + title. */
class SampleVideoGridAdapter(
    private val onClick: (SampleVideo) -> Unit
) : ListAdapter<SampleVideo, SampleVideoGridAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sample_video_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumb: ImageView = itemView.findViewById(R.id.ivThumb)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)

        fun bind(item: SampleVideo, onClick: (SampleVideo) -> Unit) {
            tvTitle.text = item.title
            Glide.with(itemView.context)
                .load(item.thumbnail)
                .transform(CenterCrop(), RoundedCorners(16))
                .placeholder(R.drawable.bg_stats_card)
                .into(ivThumb)
            itemView.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SampleVideo>() {
            override fun areItemsTheSame(old: SampleVideo, new: SampleVideo) = old.id == new.id
            override fun areContentsTheSame(old: SampleVideo, new: SampleVideo) = old == new
        }
    }
}
