package ru.spgsroot.peachy.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.spgsroot.peachy.data.vault.VaultError
import ru.spgsroot.peachy.data.vault.VaultException
import ru.spgsroot.peachy.data.vault.VaultExportResult
import ru.spgsroot.peachy.data.vault.VaultImportResult
import ru.spgsroot.peachy.data.vault.VaultManager
import java.io.IOException
import javax.inject.Inject

sealed interface VaultUiState {
    data object Idle : VaultUiState
    data class Working(val done: Int, val total: Int) : VaultUiState
    data class Exported(val result: VaultExportResult) : VaultUiState
    data class Imported(val result: VaultImportResult) : VaultUiState
    data class Failed(val reason: VaultFailure) : VaultUiState
}

enum class VaultFailure { WRONG_PASSWORD, NOT_A_VAULT, UNSUPPORTED, DAMAGED, NO_SPACE, IO, UNKNOWN }

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vault: VaultManager
) : ViewModel() {
    private val _state = MutableStateFlow<VaultUiState>(VaultUiState.Idle)
    val state: StateFlow<VaultUiState> = _state

    private var job: Job? = null

    fun export(target: Uri, password: CharArray) = run(password) {
        VaultUiState.Exported(vault.export(target, password, ::progress))
    }

    fun import(source: Uri, password: CharArray) = run(password) {
        VaultUiState.Imported(vault.import(source, password, ::progress))
    }

    /** Stops a running export or import; a vault being written is deleted, one being read is rolled back. */
    fun cancel() {
        job?.cancel()
    }

    fun reset() {
        if (job?.isActive != true) _state.value = VaultUiState.Idle
    }

    private fun progress(done: Int, total: Int) {
        _state.value = VaultUiState.Working(done, total)
    }

    /** Runs [work] and blanks [password] afterwards, however it ends. */
    private fun run(password: CharArray, work: suspend () -> VaultUiState) {
        if (job?.isActive == true) {
            password.fill('\u0000')
            return
        }
        _state.value = VaultUiState.Working(0, 0)
        job = viewModelScope.launch {
            _state.value = try {
                work()
            } catch (e: CancellationException) {
                _state.value = VaultUiState.Idle
                throw e
            } catch (e: VaultException) {
                VaultUiState.Failed(
                    when (e.error) {
                        VaultError.WRONG_PASSWORD -> VaultFailure.WRONG_PASSWORD
                        VaultError.NOT_A_VAULT -> VaultFailure.NOT_A_VAULT
                        VaultError.UNSUPPORTED_VERSION -> VaultFailure.UNSUPPORTED
                        VaultError.CORRUPT -> VaultFailure.DAMAGED
                    }
                )
            } catch (e: IllegalStateException) {
                // InternalStorageManager reports a full disk this way.
                VaultUiState.Failed(
                    if (e.message?.startsWith("Not enough free space") == true) VaultFailure.NO_SPACE
                    else VaultFailure.UNKNOWN
                )
            } catch (e: IOException) {
                VaultUiState.Failed(VaultFailure.IO)
            } catch (e: SecurityException) {
                VaultUiState.Failed(VaultFailure.IO)
            } catch (e: Exception) {
                VaultUiState.Failed(VaultFailure.UNKNOWN)
            } finally {
                password.fill('\u0000')
            }
        }
    }
}
