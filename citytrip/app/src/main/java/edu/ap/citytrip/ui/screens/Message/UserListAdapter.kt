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

class UserListAdapter(
    private val items: List<User>,
    private val onClick: (User) -> Unit
) : RecyclerView.Adapter<UserListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.txtUserName)
        val email: TextView = view.findViewById(R.id.txtUserEmail)
        val avatar: ImageView = view.findViewById(R.id.imgAvatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = items[position]
        holder.name.text = u.displayName.ifBlank { u.email }
        holder.email.text = u.email

        if (u.photoUrl != null && u.photoUrl.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(u.photoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(holder.avatar)
        } else {
            holder.avatar.setImageResource(R.drawable.ic_profile_placeholder)
        }

        holder.itemView.setOnClickListener { onClick(u) }
    }

    override fun getItemCount(): Int = items.size
}

