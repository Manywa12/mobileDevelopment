package edu.ap.citytrip.ui.screens.Message

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import edu.ap.citytrip.R
import edu.ap.citytrip.data.User

class MessagesListActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ConversationListAdapter
    private val items = mutableListOf<ConversationItem>()
    private val filteredItems = mutableListOf<ConversationItem>()

    private lateinit var recyclerTopContacts: RecyclerView
    private lateinit var topContactsAdapter: TopContactAdapter
    private val topContacts = mutableListOf<User>()

    private lateinit var editSearch: EditText
    private lateinit var emptyState: LinearLayout

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages_list)

        // Back button in custom header
        findViewById<View>(R.id.btnHeaderBack).setOnClickListener {
            finish()
        }

        recycler = findViewById(R.id.recyclerConversations)
        recyclerTopContacts = findViewById(R.id.recyclerTopContacts)
        editSearch = findViewById(R.id.editSearch)
        emptyState = findViewById(R.id.emptyState)

        adapter = ConversationListAdapter(filteredItems) { convo ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("otherUid", convo.otherUid)
            startActivity(intent)
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        topContactsAdapter = TopContactAdapter(topContacts) { user ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("otherUid", user.uid)
            startActivity(intent)
        }

        recyclerTopContacts.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerTopContacts.adapter = topContactsAdapter

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterConversations(s.toString())
            }
        })

        findViewById<FloatingActionButton>(R.id.fabNewChat).setOnClickListener {
            startActivity(Intent(this, UserListActivity::class.java))
        }

        loadTopContacts()
        listenToConversations()
    }

    private fun filterConversations(query: String) {
        filteredItems.clear()
        if (query.isBlank()) {
            filteredItems.addAll(items)
        } else {
            val lowerQuery = query.lowercase()
            filteredItems.addAll(items.filter {
                it.title.lowercase().contains(lowerQuery) ||
                it.lastMessage.lowercase().contains(lowerQuery)
            })
        }
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (filteredItems.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recycler.visibility = View.VISIBLE
        }
    }

    private fun loadTopContacts() {
        val uid = auth.currentUser?.uid ?: return

        // Load ALL Firebase users (excluding current user)
        db.collection("users")
            .get()
            .addOnSuccessListener { snapshot ->
                topContacts.clear()
                
                for (doc in snapshot.documents) {
                    val otherUid = doc.id
                    if (otherUid == uid) continue // Skip current user
                    
                    val name = doc.getString("displayName") ?: ""
                    val email = doc.getString("email") ?: ""
                    val photoUrl = doc.getString("photoUrl") ?: ""
                    
                    val user = User(
                        uid = otherUid,
                        displayName = name,
                        email = email,
                        photoUrl = photoUrl
                    )
                    
                    topContacts.add(user)
                }
                
                topContactsAdapter.notifyDataSetChanged()
            }
    }

    private fun listenToConversations() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("conversations")
            .whereArrayContains("participants", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    return@addSnapshotListener
                }

                items.clear()

                for (doc in snapshot.documents) {
                    val participants = doc.get("participants") as? List<*> ?: emptyList<Any>()
                    val otherUid = participants.firstOrNull { it != uid } as? String ?: continue

                    val lastMsg = doc.getString("lastMessage") ?: ""
                    val lastTime = doc.getTimestamp("lastMessageAt")
                    val lastTimeMillis = lastTime?.toDate()?.time ?: 0L
                    val chatId = doc.id
                    val lastSenderId = doc.getString("lastSenderId") ?: ""

                    // Get unread count from conversation document or calculate it
                    val unreadCountMap = doc.get("unreadCounts") as? Map<*, *> ?: emptyMap<Any, Any>()
                    val unreadCount = (unreadCountMap[uid] as? Number)?.toInt() ?: 0

                    db.collection("users").document(otherUid).get()
                        .addOnSuccessListener { userDoc ->
                            val title = userDoc.getString("displayName")
                                ?.takeIf { it.isNotBlank() }
                                ?: userDoc.getString("email")
                                ?: "Unknown"

                            val photoUrl = userDoc.getString("photoUrl") ?: ""

                            // Check if message is read (sender is not current user and unread count is 0)
                            val isRead = lastSenderId != uid && unreadCount == 0

                            items.add(
                                ConversationItem(
                                    chatId = chatId,
                                    otherUid = otherUid,
                                    title = title,
                                    photoUrl = photoUrl,
                                    lastMessage = lastMsg,
                                    lastMessageAt = lastTimeMillis,
                                    unreadCount = unreadCount,
                                    isOnline = false, // TODO: Implement online status with Firebase Realtime Database
                                    isMuted = false, // TODO: Implement muted status
                                    isRead = isRead
                                )
                            )

                            items.sortByDescending { it.lastMessageAt }
                            filterConversations(editSearch.text.toString())
                            updateEmptyState()
                        }
                }
            }
    }
}
