package org.calamares.miga.ui.sync

import org.calamares.miga.L10n
import org.calamares.miga.R
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.data.model.SyncConnection
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.share.SyncInvite
import org.calamares.miga.data.share.SyncInviteCodec
import org.calamares.miga.data.sync.SyncClient
import org.calamares.miga.data.sync.SyncEngine
import org.calamares.miga.data.sync.SyncInvitationResult
import org.calamares.miga.data.sync.SyncPingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface TestConnectionState {
    data object Idle : TestConnectionState
    data object Testing : TestConnectionState
    data object Success : TestConnectionState
    data class Error(val reason: String) : TestConnectionState
}

sealed interface InviteState {
    data object Idle : InviteState
    data object Loading : InviteState
    /** [payload] es el texto del QR (ver SyncInviteCodec); [label] es el nombre de la conexión desde la que se invita. */
    data class Ready(val label: String, val payload: String) : InviteState
    data class Error(val reason: String) : InviteState
}

class SyncConnectionsViewModel(private val repository: RecipeRepository) : ViewModel() {

    private val syncEngine = SyncEngine(repository)

    val connections: StateFlow<List<SyncConnection>> = repository.observeSyncConnections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _syncingConnectionIds = MutableStateFlow<Set<Long>>(emptySet())
    val syncingConnectionIds: StateFlow<Set<Long>> = _syncingConnectionIds

    /** Resultado (éxito/error) ya queda reflejado en la propia conexión, vía [connections]
     *  (lastSyncedAt/lastSyncError); esto solo controla el indicador de progreso. */
    fun syncNow(context: Context, connectionId: Long) {
        viewModelScope.launch {
            _syncingConnectionIds.value = _syncingConnectionIds.value + connectionId
            repository.resetSyncCursor(connectionId) // reparación manual: vuelve a bajar todo, recupera fotos que se perdieron
            repository.enqueueFullConnectionResync(connectionId)
            syncEngine.syncConnection(context, connectionId)
            _syncingConnectionIds.value = _syncingConnectionIds.value - connectionId
        }
    }

    private val _inviteState = MutableStateFlow<InviteState>(InviteState.Idle)
    val inviteState: StateFlow<InviteState> = _inviteState

    /** Mensaje puntual para mostrar en un snackbar (resultado de unirse por QR, etc.). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun postMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Pide al servidor un token nuevo del namespace y lo deja listo para mostrarlo como QR. */
    fun createInvite(connection: SyncConnection) {
        viewModelScope.launch {
            _inviteState.value = InviteState.Loading
            val label = L10n.str(R.string.invitation_x, LocalDate.now())
            _inviteState.value = when (val result = SyncClient.createInvitation(connection, label)) {
                is SyncInvitationResult.Success -> {
                    val invite = SyncInvite(connection.serverUrl, result.invitation.namespaceId, result.invitation.token, connection.label)
                    InviteState.Ready(connection.label, SyncInviteCodec.encode(invite))
                }
                is SyncInvitationResult.Error -> InviteState.Error(result.reason)
            }
        }
    }

    fun dismissInvite() {
        _inviteState.value = InviteState.Idle
    }

    /** Activa/desactiva compartir la lista de la compra a través de [connectionId] (como mucho una conexión a la vez). */
    fun setShoppingSync(connectionId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setShoppingSyncConnection(if (enabled) connectionId else null)
        }
    }

    /** Une esta app a un namespace a partir de una invitación escaneada: comprueba la conexión,
     *  la guarda y sincroniza (con la lista de la compra compartida si [syncShopping]). */
    fun joinFromInvite(context: Context, invite: SyncInvite, label: String, syncShopping: Boolean) {
        viewModelScope.launch {
            val candidate = SyncConnection(
                id = 0L,
                label = label,
                serverUrl = invite.serverUrl.trim().trimEnd('/'),
                namespaceId = invite.namespaceId,
                accessToken = invite.token,
                lastSyncedRevision = 0,
                lastSyncedAt = null,
                lastSyncError = null
            )
            when (val ping = SyncClient.ping(candidate)) {
                is SyncPingResult.Error -> _message.value = L10n.str(R.string.couldnt_join_x, ping.reason)
                is SyncPingResult.Success -> {
                    val id = repository.addSyncConnection(label.ifBlank { invite.namespaceId }, invite.serverUrl, invite.namespaceId, invite.token)
                    if (syncShopping) repository.setShoppingSyncConnection(id)
                    _syncingConnectionIds.value = _syncingConnectionIds.value + id
                    syncEngine.syncConnection(context, id)
                    _syncingConnectionIds.value = _syncingConnectionIds.value - id
                    _message.value = L10n.str(R.string.connection_x_added, label.ifBlank { invite.namespaceId })
                }
            }
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
