package es.upm.etsisi.mad.bioquiet.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ZepaUserRepository {

    companion object {
        private const val LOG_TAG = "ZepaUserRepository"
    }

    private val db =
        FirebaseDatabase.getInstance().reference.child("zepa_users")
    private val auth = FirebaseAuth.getInstance()

    fun enterZepa(zepaId: String) {
        val userId = auth.currentUser?.uid ?: run {
            Log.w(LOG_TAG, "User not authenticated, cannot register in ZEPA")
            return
        }
        val ref = db.child(zepaId).child(userId)
        ref.onDisconnect().removeValue()
        ref.setValue(true)
    }

    fun leaveZepa(zepaId: String) {
        val userId = auth.currentUser?.uid ?: return
        db.child(zepaId).child(userId).removeValue()
    }

    fun observeZepaUserCount(zepaId: String, onCount: (Int) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onCount(snapshot.childrenCount.toInt())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(LOG_TAG, "Failed to observe user count for ZEPA $zepaId: ${error.message}")
            }
        }
        db.child(zepaId).addValueEventListener(listener)
        return listener
    }

    fun stopObserving(zepaId: String, listener: ValueEventListener) {
        db.child(zepaId).removeEventListener(listener)
    }
}
