package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Task
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TaskRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun createTask(
        title: String,
        description: String = "",
        category: String = "عام",
        priority: String = "MEDIUM",
        dueDateTime: Long? = null,
        hasReminder: Boolean = false
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val taskId = UUID.randomUUID().toString()
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)

        val data = hashMapOf<String, Any>(
            "userId" to uid,
            "title" to title,
            "category" to category,
            "priority" to priority,
            "isCompleted" to false,
            "hasReminder" to hasReminder,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (description.isNotBlank()) data["description"] = description
        if (dueDateTime != null) data["dueDateTime"] = dueDateTime

        return try {
            docRef.set(data).await()
            Result.success(taskId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserTasks(): Result<List<Task>> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val collection = db.collection("users").document(uid).collection("tasks")
        return try {
            val snapshot = collection.get().await()
            val tasks = snapshot.toObjects(Task::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(tasks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, collection.path)
            Result.failure(e)
        }
    }

    fun observeTasks(): Flow<List<Task>> {
        val uid = requireUserId()
        val collection = db.collection("users").document(uid).collection("tasks")
        return collection.snapshots()
            .map { it.toObjects(Task::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, collection.path)
                }
                throw error
            }
    }

    suspend fun getTask(userId: String, taskId: String): Result<Task> {
        val docRef = db.collection("users").document(userId).collection("tasks").document(taskId)
        return try {
            val doc = docRef.get().await()
            val task = doc.toObject(Task::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: throw NoSuchElementException("Task not found")
            Result.success(task)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getTaskById(taskId: String): Result<Task> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        return getTask(uid, taskId)
    }

    suspend fun updateTaskCompletion(taskId: String, isCompleted: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        val updates = hashMapOf<String, Any>(
            "isCompleted" to isCompleted,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCompleted) {
            updates["completedAt"] = System.currentTimeMillis()
        }
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
