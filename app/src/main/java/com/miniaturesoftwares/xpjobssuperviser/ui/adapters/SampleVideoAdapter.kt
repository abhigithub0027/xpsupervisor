package com.miniaturesoftwares.xpjobssuperviser.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.network.SampleVideo

/** Home sample-video thumbnail card. Mirrors the recorder app's design. */
class SampleVideoAdapter(
    private val onClick: (SampleVideo) -> Unit
) : ListAdapter<SampleVideo, SampleVideoAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sample_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)

        fun bind(item: SampleVideo, onClick: (SampleVideo) -> Unit) {
            Glide.with(itemView.context)
                .load(item.thumbnail)
                .transform(CenterCrop(), RoundedCorners(16))
                .placeholder(R.drawable.bg_stats_card)
                .into(ivThumbnail)

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
