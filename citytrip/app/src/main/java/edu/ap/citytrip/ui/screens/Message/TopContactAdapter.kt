package edu.ap.citytrip.ui.screens.Message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import edu.ap.citytrip.R
import edu.ap.citytrip.data.User

class TopContactAdapter(
    private val contacts: List<User>,
    private val onClick: (User) -> Unit
) : RecyclerView.Adapter<TopContactAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.imgAvatar)
        val name: TextView = view.findViewById(R.id.txtName)
        val onlineStatus: View = view.findViewById(R.id.viewOnlineStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_contact, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val contact = contacts[position]

        holder.name.text = contact.displayName.ifBlank { contact.email }

        if (contact.photoUrl != null && contact.photoUrl.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(contact.photoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(holder.avatar)
        } else {
            holder.avatar.setImageResource(R.drawable.ic_profile_placeholder)
        }

        // TODO: Implement online status check from Firebase
        holder.onlineStatus.visibility = View.GONE

        holder.itemView.setOnClickListener { onClick(contact) }
    }

    override fun getItemCount() = contacts.size
}














