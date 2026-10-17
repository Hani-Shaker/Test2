package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Task
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TaskRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createTask_validPayload_createsDocumentAndReturnsId(): Unit = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = TaskRepository(firestore)

    val createResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createTask(TASK_TITLE) }
    assertTrue(createResult.isSuccess)
    val taskId = createResult.getOrThrow()
    assertTrue(taskId.isNotEmpty())
  }

  @Test
  fun getUserTasks_authenticatedOwner_returnsMatchingTasks(): Unit = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = TaskRepository(firestore)
    val taskId = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createTask(TASK_TITLE).getOrThrow() }

    val tasksResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getUserTasks() }
    assertTrue(tasksResult.isSuccess)
    val tasks = tasksResult.getOrThrow()
    assertTrue(tasks.any { it.id == taskId })
  }

  @Test
  fun observeTasks_authenticatedOwner_emitsRealtimeUpdates(): Unit = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = TaskRepository(firestore)
    val taskId = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createTask(TASK_TITLE).getOrThrow() }

    val emittedTasks = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeTasks().first { list: List<Task> -> list.any { it.id == taskId } }
    }
    assertTrue(emittedTasks.any { it.id == taskId })
  }

  @Test
  fun getTaskById_crossUserAccess_failsWithPermissionDenied(): Unit = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = TaskRepository(firestore)
    val taskId = withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.createTask(TASK_TITLE).getOrThrow() }

    signInTestUser(BOB_EMAIL)
    val bobRepo = TaskRepository(firestore)
    val result = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getTask(aliceUid, taskId) }
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertTrue(exception is FirebaseFirestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (exception as FirebaseFirestoreException).code)
  }

  @Test
  fun observeTasks_unauthenticatedUser_failsWithPermissionDenied(): Unit = runBlocking {
    auth.signOut()
    val repository = TaskRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) { repository.observeTasks().first() }
      fail("Expected IllegalStateException but none thrown")
    } catch (e: IllegalStateException) {
      assertTrue(e.message?.contains("User must be signed in") == true)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice@test.com"
    const val BOB_EMAIL = "bob@test.com"
    const val TASK_TITLE = "Sample Task"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
