package com.nadidstudio.nexis.ui.screens.projects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nadidstudio.nexis.assistants.Conversation
import com.nadidstudio.nexis.assistants.Project
import com.nadidstudio.nexis.data.InMemoryAppStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lists the Conversations inside one Project and lets the user start a new
 * one. Opening a conversation is wired up to a callback but the actual chat
 * screen (sending/receiving messages via BaseAssistant.sendMessage) is the
 * next piece to build — [onOpenConversation] is currently a TODO in NavHost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexisConversationListScreen(
    project: Project,
    onBack: () -> Unit,
    onOpenConversation: (Conversation) -> Unit
) {
    // Project.conversations is a plain MutableList (the domain model has no
    // Compose dependency by design), so we mirror it into Compose state here
    // to get recomposition when a new conversation is created.
    val conversations = remember(project.id) {
        mutableStateListOf<Conversation>().apply { addAll(project.conversations) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val convo = InMemoryAppStore.createConversation(project)
                conversations.add(convo)
            }) {
                Icon(Icons.Filled.Add, contentDescription = "New conversation")
            }
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Text("No conversations yet. Tap + to start one.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp)
            ) {
                items(conversations) { convo ->
                    Card(
                        onClick = { onOpenConversation(convo) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (convo.messages.isEmpty()) "New conversation" else convo.messages.last().text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2
                            )
                            Text(
                                text = formatTime(convo.messages.lastOrNull()?.timestampMillis),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long?): String {
    if (millis == null) return ""
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(Date(millis))
}
