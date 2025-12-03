package edu.ap.citytrip.ui.screens.Message

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import edu.ap.citytrip.R
import edu.ap.citytrip.data.User

data class SimpleUser(
    val uid: String = "",
    val displayName: String = "",
    val email: String = ""
)

class UserListActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: UserListAdapter
    private val users = mutableListOf<User>()

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_list)

        recycler = findViewById(R.id.recyclerUsers)

        adapter = UserListAdapter(users) { user ->
            // User geklikt → open ChatActivity met deze user
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("otherUid", user.uid)
            startActivity(intent)
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        loadUsers()
    }

    private fun loadUsers() {
        val myUid = auth.currentUser?.uid ?: return

        db.collection("users")
            .get()
            .addOnSuccessListener { snap ->
                users.clear()
                snap.documents.forEach { doc ->
                    val uid = doc.id
                    if (uid == myUid) return@forEach // Skip current user
                    
                    val name = doc.getString("displayName") ?: ""
                    val email = doc.getString("email") ?: ""
                    val photoUrl = doc.getString("photoUrl") ?: ""

                    users.add(
                        User(
                            uid = uid,
                            displayName = name,
                            email = email,
                            photoUrl = photoUrl
                        )
                    )
                }

                adapter.notifyDataSetChanged()
            }
    }
}
