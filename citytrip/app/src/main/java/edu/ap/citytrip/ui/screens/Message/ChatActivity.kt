package edu.ap.citytrip.ui.screens.Message

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import edu.ap.citytrip.R
import edu.ap.citytrip.data.Message
import edu.ap.citytrip.data.getConversationId

class ChatActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: MessagesAdapter
    private val messages = mutableListOf<Message>()

    private lateinit var input: EditText
    private lateinit var btnSend: ImageButton

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var convId: String
    private lateinit var otherUid: String
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        otherUid = intent.getStringExtra("otherUid")!!
        convId = getConversationId(auth.currentUser!!.uid, otherUid)

        recycler = findViewById(R.id.recyclerMessages)
        adapter = MessagesAdapter(messages, auth.currentUser!!.uid)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter

        input = findViewById(R.id.editMessage)
        btnSend = findViewById(R.id.btnSend)

        // Header: title + back button
        val titleView: android.widget.TextView = findViewById(R.id.txtChatTitle)
        val backButton: android.widget.ImageButton = findViewById(R.id.btnChatBack)
        val chatTitle = intent.getStringExtra("chatTitle")
        titleView.text = chatTitle ?: getString(R.string.title_messages)
        backButton.setOnClickListener { finish() }

        btnSend.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                input.setText("")
            }
        }

        createConversationIfNeeded(otherUid)
        listenToMessages()
    }

    private fun createConversationIfNeeded(otherUid: String) {
        val convRef = db.collection("conversations").document(convId)
        val myUid = auth.currentUser!!.uid
        convRef.get().addOnSuccessListener { snap ->
            if (!snap.exists()) {
                val map = hashMapOf(
                    "participants" to listOf(myUid, otherUid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastMessage" to "",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastSenderId" to "",
                    "unreadCounts" to hashMapOf(
                        myUid to 0,
                        otherUid to 0
                    )
                )
                convRef.set(map)
            } else {
                // Reset unread count when opening chat
                val unreadCounts = (snap.get("unreadCounts") as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()
                unreadCounts[myUid] = 0
                convRef.update("unreadCounts", unreadCounts)
            }
        }
    }

    private fun listenToMessages() {
        val ref = db.collection("conversations")
            .document(convId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        listener = ref.addSnapshotListener { snap, _ ->
            if (snap != null) {
                messages.clear()
                for (doc in snap.documents) {
                    val msg = doc.toObject(Message::class.java)
                    if (msg != null) messages.add(msg)
                }
                adapter.notifyDataSetChanged()
                recycler.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun sendMessage(text: String) {
        val myUid = auth.currentUser!!.uid
        val convRef = db.collection("conversations").document(convId)
        
        convRef.get().addOnSuccessListener { convDoc ->
            val participantsList = convDoc.get("participants") as? List<*> ?: emptyList<Any>()
            val otherUidInConv = participantsList.firstOrNull { it != myUid } as? String ?: otherUid
            val participants = if (participantsList.isNotEmpty()) participantsList else listOf(myUid, otherUidInConv)
            
            val msg = hashMapOf(
                "senderId" to myUid,
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp(),
                "read" to false,
                "participants" to participants // Store participants in message for security rules
            )

            db.collection("conversations")
                .document(convId)
                .collection("messages")
                .add(msg)
                .addOnSuccessListener {
                    // Update unread counts
                    val unreadCounts = (convDoc.get("unreadCounts") as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()
                    val currentCount = (unreadCounts[otherUidInConv] as? Number)?.toInt() ?: 0
                    unreadCounts[otherUidInConv] = currentCount + 1

                    convRef.update(
                        mapOf(
                            "lastMessage" to text,
                            "lastMessageAt" to FieldValue.serverTimestamp(),
                            "lastSenderId" to myUid,
                            "unreadCounts" to unreadCounts
                        )
                    )
                }
        }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
    }
}