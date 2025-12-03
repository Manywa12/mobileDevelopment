package edu.ap.citytrip.ui.screens.Message

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import edu.ap.citytrip.R
import java.text.SimpleDateFormat
import java.util.*

class ConversationListAdapter(
    private val items: List<ConversationItem>,
    private val onClick: (ConversationItem) -> Unit
) : RecyclerView.Adapter<ConversationListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.imgAvatar)
        val title: TextView = view.findViewById(R.id.txtTitle)
        val lastMsg: TextView = view.findViewById(R.id.txtLastMessage)
        val time: TextView = view.findViewById(R.id.txtTime)
        val unreadCount: TextView = view.findViewById(R.id.txtUnreadCount)
        val onlineStatus: View = view.findViewById(R.id.viewOnlineStatus)
        val mutedIcon: ImageView = view.findViewById(R.id.imgMuted)
        val readStatus: ImageView = view.findViewById(R.id.imgReadStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.title.text = item.title
        holder.lastMsg.text = item.lastMessage.ifBlank { "No messages yet" }

        // Format timestamp
        val timeStr = formatTimestamp(item.lastMessageAt)
        holder.time.text = timeStr

        // Load avatar
        if (item.photoUrl.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(item.photoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(holder.avatar)
        } else {
            holder.avatar.setImageResource(R.drawable.ic_profile_placeholder)
        }

        // Online status
        holder.onlineStatus.visibility = if (item.isOnline) View.VISIBLE else View.GONE

        // Unread count
        if (item.unreadCount > 0) {
            holder.unreadCount.visibility = View.VISIBLE
            holder.unreadCount.text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString()
            holder.unreadCount.background = ContextCompat.getDrawable(
                holder.itemView.context,
                R.drawable.bg_unread_badge
            )
        } else {
            holder.unreadCount.visibility = View.GONE
        }

        // Muted icon
        holder.mutedIcon.visibility = if (item.isMuted) View.VISIBLE else View.GONE

        // Read status (checkmark)
        holder.readStatus.visibility = if (item.isRead && item.unreadCount == 0) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0) return ""
        
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val nowCalendar = Calendar.getInstance()
        
        return when {
            diff < 60 * 1000 -> "Just now"
            diff < 24 * 60 * 60 * 1000 && calendar.get(Calendar.DAY_OF_YEAR) == nowCalendar.get(Calendar.DAY_OF_YEAR) -> {
                // Today - show time
                DateFormat.format("h:mm a", Date(timestamp)).toString()
            }
            diff < 7 * 24 * 60 * 60 * 1000 -> {
                // This week - show day name
                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
            }
            calendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR) -> {
                // This year - show month and day
                SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
            }
            else -> {
                // Older - show full date
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }

    override fun getItemCount() = items.size
}
