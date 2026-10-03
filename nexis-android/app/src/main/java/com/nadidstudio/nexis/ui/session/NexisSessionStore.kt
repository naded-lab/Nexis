package com.nadidstudio.nexis.ui.session

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nadidstudio.nexis.assistants.AssistantRole
import com.nadidstudio.nexis.assistants.BaseAssistant
import com.nadidstudio.nexis.assistants.ChatAssistant
import com.nadidstudio.nexis.assistants.ChatMessage
import com.nadidstudio.nexis.assistants.CodingAssistant
import com.nadidstudio.nexis.assistants.LocalAssistant
import com.nadidstudio.nexis.assistants.Conversation
import com.nadidstudio.nexis.assistants.Project
import com.nadidstudio.nexis.data.InMemoryAppStore
import com.nadidstudio.nexis.head.ModelHealthTracker
import com.nadidstudio.nexis.head.NetworkMonitor
import com.nadidstudio.nexis.orchestration.FallbackOrchestrator
import com.nadidstudio.nexis.orchestration.ModelRegistry
import com.nadidstudio.nexis.orchestration.OrchestratedResult
import com.nadidstudio.nexis.security.SecureKeyStore
import com.nadidstudio.nexis.ui.screens.providerDisplayName

/** UI-facing chat bubble — same shape the ChatScreen design already expects. */
data class UiMessage(val fromUser: Boolean, val text: String, val time: String, val index: Int = -1)

/**
 * Real session store behind the uploaded UI design (Drawer/ChatScreen/
 * Projects/Settings). Replaces the design's original placeholder
 * `NexisStore` (fake in-memory list + fake delay). Visuals are untouched —
 * only the data layer underneath changed: every message now goes through
 * [BaseAssistant.sendMessage] -> [FallbackOrchestrator], real per-project
 * conversation isolation, and real per-provider API keys.
 */
object NexisSessionStore {

    private lateinit var keyStore: SecureKeyStore
    private lateinit var orchestrator: FallbackOrchestrator
    private lateinit var healthTracker: ModelHealthTracker
    private lateinit var codingAssistant: CodingAssistant
    private lateinit var chatAssistant: ChatAssistant
    private lateinit var localAssistant: LocalAssistant
    private var initialized = false

    /** Call once, e.g. from MainActivity.onCreate(applicationContext). Safe to call more than once. */
    fun init(context: Context) {
        com.nadidstudio.nexis.data.AssistantPrefs.attach(context)
        if (initialized) return
        InMemoryAppStore.attach(context)
        com.nadidstudio.nexis.data.CustomModelStore.registerAll(context)
        com.nadidstudio.nexis.orchestration.ModelRegistry.registerCustomAdapter(com.nadidstudio.nexis.models.LocalModelAdapter(context.applicationContext))
        InMemoryAppStore.savedChains.forEach { (roleName, chain) ->
            runCatching { AssistantRole.valueOf(roleName) }.getOrNull()?.let { roleChains[it] = chain }
        }
        keyStore = SecureKeyStore(context.applicationContext)
        com.nadidstudio.nexis.backup.GitHubBackup.attach(context, keyStore)
        healthTracker = ModelHealthTracker(context.applicationContext.getSharedPreferences("nexis_key_health", Context.MODE_PRIVATE))
        orchestrator = FallbackOrchestrator(
            keyStore = keyStore,
            networkMonitor = NetworkMonitor(context.applicationContext),
            healthTracker = healthTracker
        )
        codingAssistant = CodingAssistant(orchestrator)
        chatAssistant = ChatAssistant(orchestrator)
        localAssistant = LocalAssistant(orchestrator)
        // Ensure there's always a project to land the "quick chat" in for each role.
        if (InMemoryAppStore.projectsFor(AssistantRole.CODING).isEmpty()) {
            InMemoryAppStore.createProject(AssistantRole.CODING, "محادثة سريعة")
        }
        if (InMemoryAppStore.projectsFor(AssistantRole.CHAT).isEmpty()) {
            InMemoryAppStore.createProject(AssistantRole.CHAT, "محادثة سريعة")
        }
        if (InMemoryAppStore.projectsFor(AssistantRole.LOCAL).isEmpty()) {
            InMemoryAppStore.createProject(AssistantRole.LOCAL, "محادثة سريعة")
        }
        initialized = true
        openProject(InMemoryAppStore.projectsFor(AssistantRole.CODING).first())
    }

    /** Call after a restore replaced the local data: rebuilds chains and reopens a valid project. */
    fun reloadAfterRestore() {
        roleChains.clear()
        InMemoryAppStore.savedChains.forEach { (roleName, chain) ->
            runCatching { AssistantRole.valueOf(roleName) }.getOrNull()?.let { roleChains[it] = chain }
        }
        if (InMemoryAppStore.projectsFor(AssistantRole.CODING).isEmpty()) InMemoryAppStore.createProject(AssistantRole.CODING, "محادثة سريعة")
        if (InMemoryAppStore.projectsFor(AssistantRole.CHAT).isEmpty()) InMemoryAppStore.createProject(AssistantRole.CHAT, "محادثة سريعة")
        if (InMemoryAppStore.projectsFor(AssistantRole.LOCAL).isEmpty()) InMemoryAppStore.createProject(AssistantRole.LOCAL, "محادثة سريعة")
        openProject(InMemoryAppStore.projectsFor(AssistantRole.CODING).first())
    }

    /** Pre-warms the encrypted key store off the main thread right after
     *  launch, so the first screen that touches API keys (Settings, the
     *  model sheet) never blocks the UI thread on Keystore setup. Call once
     *  from a background coroutine, e.g. MainActivity.onCreate via lifecycleScope. */
    suspend fun warmUpSecureStorage() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            keyStore.warmUp()
        }
    }

    var selectedRole by mutableStateOf(AssistantRole.CODING)
        private set

    var selectedProject by mutableStateOf<Project?>(null)
        private set

    private var currentConversation by mutableStateOf<Conversation?>(null)

    var messages by mutableStateOf<List<UiMessage>>(emptyList())
        private set

    /** Ids of conversations that are waiting for a reply right now. Per-conversation,
     *  so a pending reply in one chat never blocks typing/sending in another. */
    private val sendingIds = mutableStateListOf<String>()

    val sending: Boolean get() = currentConversation?.id?.let { it in sendingIds } == true

    /** Sends outlive whichever screen started them (leaving the chat used to cancel
     *  the coroutine and leave the "sending" flag stuck forever). */
    private val jobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val stopped: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    /** Text already produced at the moment Stop was pressed — kept in the chat. */
    private val stopSnapshots = mutableMapOf<String, String?>()
    /** Conversations whose current request may run on the on-device model. */
    private val localIds = mutableSetOf<String>()
    private val revealIds = mutableStateListOf<String>()

    /** Reply text produced so far, per conversation (real tokens for the local model, typed-out for remote ones). */
    val streamingTexts = mutableStateMapOf<String, String>()
    val streamingText: String? get() = currentConversation?.id?.let { streamingTexts[it] }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Every conversation that has at least one message, newest first, across all assistants/projects. */
    /** Bumped whenever a conversation's title/pin/messages change, so the drawer list recomposes. */
    var listVersion by mutableStateOf(0)
        private set

    fun allConversations(): List<Pair<Project, Conversation>> =
        listVersion.let { (InMemoryAppStore.codingProjects + InMemoryAppStore.chatProjects + InMemoryAppStore.localProjects)
            .flatMap { p -> p.conversations.filter { it.messages.isNotEmpty() }.map { p to it } }
            .sortedByDescending { it.second.messages.last().timestampMillis } }

    fun isConversationSending(id: String) = id in sendingIds
    val currentConversationId: String? get() = currentConversation?.id

    /** Opens any saved conversation (from the drawer list), switching project/assistant as needed. */
    fun openConversation(project: Project, conversation: Conversation) {
        selectedProject = project
        selectedRole = project.assistantRole
        currentConversation = conversation
        lastError = null
        lastNotice = null
        syncMessagesFromConversation()
    }

    var lastError by mutableStateOf<String?>(null)
        private set

    var lastNotice by mutableStateOf<String?>(null)
        private set

    /** Imports a picked file into [project] (default: the open project) and reports the outcome. */
    fun importFile(context: Context, uri: android.net.Uri, project: Project? = selectedProject) {
        val target = project ?: return
        val err = com.nadidstudio.nexis.data.ProjectFiles.import(context, target, uri)
        lastError = err
        lastNotice = if (err == null) "أُضيف الملف إلى مشروع «${target.name}»" else null
    }

    val keyStoreForSettings: SecureKeyStore get() = keyStore
    /** Cloud (API-key) providers only — the on-device model is its own assistant and never mixes in. */
    val providerIds: List<String> get() = ModelRegistry.allProviderIds().filter { it != "local" }

    /** Per-assistant-role model chain, filtered by the toggle in Settings/model picker. */
    private val roleChains = mutableStateMapOf<AssistantRole, List<String>>()

    fun chainFor(role: AssistantRole): List<String> =
        if (role == AssistantRole.LOCAL) listOf("local")
        else (roleChains[role] ?: ModelRegistry.defaultChainFor(role)).filterNot { it == "local" }

    /** Status dot for the quick model switcher: ready / paused by limit / bad key / no key. */
    fun providerStatus(providerId: String): com.nadidstudio.nexis.head.ProviderStatus =
        healthTracker.providerStatus(providerId, keyStore.getKeys(providerId).map { it.id })

    /** Epoch ms when this provider's paused keys come back, or null if it isn't limited. */
    fun providerLimitedUntil(providerId: String): Long? =
        healthTracker.limitedUntil(providerId, keyStore.getKeys(providerId).map { it.id })

    /** Manual reset from the key dialog: clears pauses / bad-key marks for every key of this provider. */
    fun resetProviderLimits(providerId: String) =
        healthTracker.resetProvider(providerId, keyStore.getKeys(providerId).map { it.id })

    fun isProviderEnabled(role: AssistantRole, providerId: String): Boolean =
        chainFor(role).contains(providerId)

    /** Toggles one provider in/out of this role's fallback chain (order preserved). */
    fun toggleProvider(role: AssistantRole, providerId: String, enabled: Boolean) {
        val current = chainFor(role)
        roleChains[role] = if (enabled) {
            if (current.contains(providerId)) current else (current + providerId).take(5)
        } else {
            current.filterNot { it == providerId }
        }
        InMemoryAppStore.savedChains[role.name] = roleChains[role] ?: emptyList()
        InMemoryAppStore.persist()
    }

    /** Makes [providerId] the active (first) model of this role's chain; the others stay as fallbacks. */
    fun setActiveProvider(role: AssistantRole, providerId: String) {
        if (role == AssistantRole.LOCAL || providerId == "local") return
        roleChains[role] = (listOf(providerId) + chainFor(role).filterNot { it == providerId }).take(5)
        InMemoryAppStore.savedChains[role.name] = roleChains[role] ?: emptyList()
        InMemoryAppStore.persist()
    }

    fun selectRole(role: AssistantRole) {
        selectedRole = role
        val project = InMemoryAppStore.projectsFor(role).firstOrNull()
            ?: InMemoryAppStore.createProject(role, "محادثة سريعة")
        openProject(project)
    }

    fun openProject(project: Project) {
        selectedProject = project
        selectedRole = project.assistantRole
        currentConversation = project.conversations.lastOrNull()
            ?: InMemoryAppStore.createConversation(project)
        lastError = null
        syncMessagesFromConversation()
    }

    fun newChat() {
        val project = selectedProject ?: return
        // Reuse the current chat if it is still empty instead of piling up blank ones.
        val cur = currentConversation
        if (cur == null || cur.messages.isNotEmpty()) {
            currentConversation = project.conversations.lastOrNull { it.messages.isEmpty() && it.id !in sendingIds }
                ?: InMemoryAppStore.createConversation(project)
        }
        lastError = null
        syncMessagesFromConversation()
    }

    private fun syncMessagesFromConversation() {
        val conversation = currentConversation ?: return
        // While a remote reply is being "typed out", its full text is already in the
        // conversation — hide it so only the growing streaming bubble is visible.
        val visible = if (conversation.id in revealIds) conversation.messages.dropLast(1) else conversation.messages.toList()
        messages = visible.mapIndexed { i, m -> UiMessage(fromUser = m.role == "user", text = m.text, time = "", index = i) }
    }

    fun addUserMessageOptimistically(text: String) {
        messages = messages + UiMessage(true, text, "الآن", messages.size)
    }

    /** Fire-and-forget send through the real fallback orchestrator (runs in an app-level scope). */
    fun send(text: String, chainOverride: List<String>? = null) {
        val conversation = currentConversation ?: return
        if (conversation.id in sendingIds) return
        val role = selectedRole
        val chain = chainOverride ?: chainFor(role)
        val assistant: BaseAssistant = when (role) {
            AssistantRole.CODING -> codingAssistant
            AssistantRole.CHAT -> chatAssistant
            AssistantRole.LOCAL -> localAssistant
        }
        val id = conversation.id
        sendingIds.add(id)
        lastError = null
        lastNotice = null
        val usesLocal = "local" in chain
        if (usesLocal) localIds.add(id)
        val job = appScope.launch {
            var error: String? = null
            var notice: String? = null
            try {
                if (usesLocal) {
                    // Live tokens from the native side (any thread); ignored once Stop was pressed.
                    com.nadidstudio.nexis.engine.LocalLlamaEngine.partialListener = { t -> if (id !in stopped) streamingTexts[id] = t }
                }
                when (val result = assistant.sendMessage(conversation, text, chain)) {
                    is OrchestratedResult.Success -> {
                        // Local replies were already streamed token by token; remote ones are typed out.
                        if (result.providerId != "local") reveal(conversation, result.text)
                        val wanted = chain.firstOrNull()
                        if (wanted != null && wanted != result.providerId) {
                            val until = providerLimitedUntil(wanted)?.let { " (حتى ${com.nadidstudio.nexis.ui.screens.formatLimitTime(it)})" }.orEmpty()
                            notice = "تعذّر ${providerDisplayName(wanted)}$until — أجاب ${providerDisplayName(result.providerId)}"
                        }
                    }
                    is OrchestratedResult.Offline -> error = "لا يوجد اتصال بالإنترنت"
                    is OrchestratedResult.AllModelsFailed -> {
                        // Report the model the user actually picked (first attempt), not the last fallback tried.
                        val detail = result.attempts.firstOrNull { it.contains("←") }
                        error = when {
                            result.attempts.isEmpty() -> "لا يوجد مفتاح API لأي نموذج مفعّل — أضف مفتاحًا من الإعدادات أو اختر النموذج المحلي"
                            detail != null -> "فشل النموذج — $detail"
                            else -> "كل النماذج المتاحة فشلت — تحقق من مفاتيح API في الإعدادات"
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                stopped.add(id)
                keepPartialReply(conversation, stopSnapshots[id] ?: streamingTexts[id])
            } catch (e: Throwable) {
                error = "خطأ غير متوقع: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                if (usesLocal) com.nadidstudio.nexis.engine.LocalLlamaEngine.partialListener = null
                localIds.remove(id)
                stopSnapshots.remove(id)
                streamingTexts.remove(id)
                revealIds.remove(id)
                sendingIds.remove(id)
                jobs.remove(id)
                InMemoryAppStore.persist()
                listVersion++
            }
            val wasStopped = stopped.remove(id)
            // Only touch the visible chat if the user is still looking at this conversation.
            if (currentConversation?.id == id) {
                lastError = if (wasStopped) null else error
                lastNotice = if (wasStopped) null else notice
                syncMessagesFromConversation()
            }
        }
        jobs[id] = job
    }

    /** Types the finished remote reply out progressively (the providers return whole replies). */
    private suspend fun reveal(conversation: Conversation, full: String) {
        val id = conversation.id
        revealIds.add(id)
        if (currentConversation?.id == id) syncMessagesFromConversation()
        val step = maxOf(3, full.length / 220)
        var i = 0
        while (i < full.length) {
            i = minOf(full.length, i + step)
            streamingTexts[id] = full.substring(0, i)
            kotlinx.coroutines.delay(16)
        }
    }

    /**
     * Stop button: really cancels the running work — the HTTP request (cloud), the
     * native generation loop (local) and the typing reveal — keeps whatever text was
     * already produced, and stops any further token updates for this conversation.
     */
    fun stop() {
        val id = currentConversation?.id ?: return
        if (id !in sendingIds) return
        stopped.add(id)                       // blocks any late token/update immediately
        stopSnapshots[id] = streamingTexts[id]
        if (id in localIds) com.nadidstudio.nexis.engine.LocalLlamaEngine.abort()
        jobs[id]?.cancel()
    }

    /** Keeps the already-generated part of an interrupted reply inside the conversation. */
    private fun keepPartialReply(c: Conversation, partial: String?) {
        val text = partial?.trim().orEmpty()
        val last = c.messages.lastOrNull() ?: return
        if (last.role == "assistant" && c.id in revealIds) {
            // Remote reply was fully stored but only partly "typed" — keep just the typed part.
            c.messages.removeAt(c.messages.lastIndex)
            if (text.isNotEmpty()) c.messages.add(ChatMessage(role = "assistant", text = text))
        } else if (last.role == "user" && text.isNotEmpty()) {
            c.messages.add(ChatMessage(role = "assistant", text = text))
        }
    }

    /**
     * Re-asks the last question with another model for this one answer only: the chosen
     * model goes first, the role's usual chain stays behind it as fallback, and the
     * role's saved active model is NOT changed.
     */
    fun regenerateWith(providerId: String) {
        val role = selectedRole
        if (role == AssistantRole.LOCAL) return
        regenerate((listOf(providerId) + chainFor(role).filterNot { it == providerId }).take(5))
    }

    /** Re-asks the last question (drops the last answer). */
    fun regenerate(chainOverride: List<String>? = null) {
        val c = currentConversation ?: return
        if (c.id in sendingIds || c.messages.isEmpty()) return
        if (c.messages.last().role == "assistant") c.messages.removeAt(c.messages.lastIndex)
        val lastUser = c.messages.lastOrNull() ?: return
        if (lastUser.role != "user") return
        c.messages.removeAt(c.messages.lastIndex)
        syncMessagesFromConversation()
        addUserMessageOptimistically(lastUser.text)
        send(lastUser.text, chainOverride)
    }

    /** Edit: removes that message and everything after it, returns its text to put back in the input. */
    fun takeForEdit(uiIndex: Int): String? {
        val c = currentConversation ?: return null
        if (c.id in sendingIds || uiIndex !in c.messages.indices) return null
        val text = c.messages[uiIndex].text
        while (c.messages.size > uiIndex) c.messages.removeAt(c.messages.lastIndex)
        syncMessagesFromConversation()
        InMemoryAppStore.persist()
        listVersion++
        return text
    }

    fun renameConversation(c: Conversation, name: String) {
        c.customTitle = name.trim().ifBlank { null }
        InMemoryAppStore.persist(); listVersion++
    }

    fun togglePin(c: Conversation) {
        c.pinned = !c.pinned
        InMemoryAppStore.persist(); listVersion++
    }

    fun deleteConversation(project: Project, c: Conversation) {
        jobs[c.id]?.cancel()
        project.conversations.remove(c)
        if (currentConversation?.id == c.id) {
            currentConversation = project.conversations.lastOrNull() ?: InMemoryAppStore.createConversation(project)
            lastError = null
            syncMessagesFromConversation()
        }
        InMemoryAppStore.persist(); listVersion++
    }

    /** Deletes every saved conversation in every project (files and projects are kept). */
    fun deleteAllConversations() {
        (InMemoryAppStore.codingProjects + InMemoryAppStore.chatProjects + InMemoryAppStore.localProjects).forEach { p ->
            p.conversations.toList().forEach { deleteConversation(p, it) }
        }
        InMemoryAppStore.persist(); listVersion++
    }
}
