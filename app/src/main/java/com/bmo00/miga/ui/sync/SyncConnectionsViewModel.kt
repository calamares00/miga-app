package com.bmo00.miga.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.model.SyncConnection
import com.bmo00.miga.data.repository.RecipeRepository
import com.bmo00.miga.data.sync.SyncClient
import com.bmo00.miga.data.sync.SyncEngine
import com.bmo00.miga.data.sync.SyncPingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TestConnectionState {
    data object Idle : TestConnectionState
    data object Testing : TestConnectionState
    data object Success : TestConnectionState
    data class Error(val reason: String) : TestConnectionState
}

class SyncConnectionsViewModel(private val repository: RecipeRepository) : ViewModel() {

    private val syncEngine = SyncEngine(repository)

    val connections: StateFlow<List<SyncConnection>> = repository.observeSyncConnections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _syncingConnectionIds = MutableStateFlow<Set<Long>>(emptySet())
    val syncingConnectionIds: StateFlow<Set<Long>> = _syncingConnectionIds

    /** Resultado (éxito/error) ya queda reflejado en la propia conexión, vía [connections]
     *  (lastSyncedAt/lastSyncError); esto solo controla el indicador de progreso. */
    fun syncNow(connectionId: Long) {
        viewModelScope.launch {
            _syncingConnectionIds.value = _syncingConnectionIds.value + connectionId
            syncEngine.syncConnection(connectionId)
            _syncingConnectionIds.value = _syncingConnectionIds.value - connectionId
        }
    }

    private val _testState = MutableStateFlow<TestConnectionState>(TestConnectionState.Idle)
    val testState: StateFlow<TestConnectionState> = _testState

    fun resetTestState() {
        _testState.value = TestConnectionState.Idle
    }

    /** Prueba una conexión sin guardarla todavía; usado por el diálogo de "Añadir conexión". */
    fun testConnection(serverUrl: String, namespaceId: String, accessToken: String) {
        viewModelScope.launch {
            _testState.value = TestConnectionState.Testing
            val candidate = SyncConnection(
                id = 0L,
                label = "",
                serverUrl = serverUrl.trim().trimEnd('/'),
                namespaceId = namespaceId.trim(),
                accessToken = accessToken.trim(),
                lastSyncedRevision = 0,
                lastSyncedAt = null,
                lastSyncError = null
            )
            _testState.value = when (val result = SyncClient.ping(candidate)) {
                is SyncPingResult.Success -> TestConnectionState.Success
                is SyncPingResult.Error -> TestConnectionState.Error(result.reason)
            }
        }
    }

    fun addConnection(label: String, serverUrl: String, namespaceId: String, accessToken: String) {
        viewModelScope.launch {
            repository.addSyncConnection(label, serverUrl, namespaceId, accessToken)
        }
    }

    fun removeConnection(id: Long) {
        viewModelScope.launch {
            repository.removeSyncConnection(id)
        }
    }
}
