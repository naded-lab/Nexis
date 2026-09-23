package com.nadidstudio.nexis.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.nadidstudio.nexis.assistants.AssistantRole
import com.nadidstudio.nexis.assistants.Conversation
import com.nadidstudio.nexis.assistants.Project
import java.util.UUID

/**
 * Data store for Projects/Conversations, now persisted locally as JSON
 * (see [LocalPersistence]). GitHub backup is a separate later step.
 */
object InMemoryAppStore {
    private var persistence: LocalPersistence? = null

    /** Per-role model chains (role name -> provider ids); persisted alongside the projects. */
    val savedChains: MutableMap<String, List<String>> = mutableMapOf()

    /** Loads previously saved projects/conversations/chains. Call once at startup, before any read. */
    fun attach(context: Context) {
        if (persistence != null) return
        val p = LocalPersistence(context.applicationContext)
        persistence = p
        p.load()?.let { state ->
            state.projects.forEach { proj -> listFor(proj.assistantRole).add(proj) }
            savedChains.putAll(state.chains)
        }
    }

    fun isValidBackup(json: String): Boolean = persistence?.isValid(json) ?: false

    /** Replaces all local data with [json] (a backup) and reloads it into memory. */
    fun restoreFromJson(json: String) {
        val p = persistence ?: return
        p.replaceRaw(json)
        codingProjects.clear(); chatProjects.clear(); savedChains.clear()
        p.load()?.let { state ->
            state.projects.forEach { proj -> listFor(proj.assistantRole).add(proj) }
            savedChains.putAll(state.chains)
        }
    }

    /** Writes the current state to disk. Call after any change (project, conversation, message, chain). */
    fun persist() {
        persistence?.save(PersistedState(codingProjects.toList() + chatProjects.toList(), savedChains.toMap()))
        com.nadidstudio.nexis.backup.GitHubBackup.onChanged()
    }

    val codingProjects = mutableStateListOf<Project>()
    val chatProjects = mutableStateListOf<Project>()

    private fun listFor(role: AssistantRole) =
        if (role == AssistantRole.CODING) codingProjects else chatProjects

    fun projectsFor(role: AssistantRole): List<Project> = listFor(role)

    fun createProject(role: AssistantRole, name: String): Project {
        val project = Project(id = UUID.randomUUID().toString(), name = name, assistantRole = role)
        listFor(role).add(project)
        persist()
        return project
    }

    fun findProject(projectId: String): Project? =
        (codingProjects + chatProjects).find { it.id == projectId }

    fun createConversation(project: Project): Conversation {
        val conversation = Conversation(id = UUID.randomUUID().toString(), projectId = project.id)
        project.conversations.add(conversation)
        persist()
        return conversation
    }
}
