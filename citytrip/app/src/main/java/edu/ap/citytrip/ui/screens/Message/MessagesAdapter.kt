package edu.ap.citytrip.ui.screens.Message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import edu.ap.citytrip.R
import edu.ap.citytrip.data.Message

class MessagesAdapter(
    private val items: List<Message>,
    private val myUid: String
) : RecyclerView.Adapter<MessagesAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: TextView = view.findViewById(R.id.messageBubble)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = items[position]
        holder.bubble.text = msg.text

        val params = holder.bubble.layoutParams as ViewGroup.MarginLayoutParams

        if (msg.senderId == myUid) {
            holder.bubble.setBackgroundResource(R.drawable.bg_bubble_me)
            params.marginStart = 80
            params.marginEnd = 8
        } else {
            holder.bubble.setBackgroundResource(R.drawable.bg_bubble_other)
            params.marginStart = 8
            params.marginEnd = 80
        }

        holder.bubble.layoutParams = params
    }

    override fun getItemCount() = items.size
}
