package com.personal.flowreader.library.plugin.royalroad

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.flowreader.FlowApp
import com.personal.flowreader.library.plugin.LibraryPluginActions
import com.personal.flowreader.library.plugin.SourceWork
import com.personal.flowreader.library.plugin.SourceWorkDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RoyalRoadUi(
    val query: String = "",
    val urlDraft: String = "",
    val browseOrder: String = "best-rated",
    val following: Boolean = false,
    val works: List<SourceWork> = emptyList(),
    val fiction: SourceWorkDetail? = null,
    val loggedIn: Boolean = false,
    val loginEmail: String = "",
    val showLogin: Boolean = false,
    val emailDraft: String = "",
    val passwordDraft: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

class RoyalRoadViewModel(app: Application) : AndroidViewModel(app) {
    private val flow = app as FlowApp
    private val repo = flow.royalRoad
    private val _ui = MutableStateFlow(
        RoyalRoadUi(
            loggedIn = repo.isLoggedIn(),
            loginEmail = repo.email(),
            emailDraft = repo.email(),
        ),
    )
    val ui: StateFlow<RoyalRoadUi> = _ui

    fun setQuery(value: String) {
        _ui.value = _ui.value.copy(query = value)
    }

    fun setUrlDraft(value: String) {
        _ui.value = _ui.value.copy(urlDraft = value)
    }

    fun setEmailDraft(value: String) {
        _ui.value = _ui.value.copy(emailDraft = value)
    }

    fun setPasswordDraft(value: String) {
        _ui.value = _ui.value.copy(passwordDraft = value)
    }

    fun setShowLogin(show: Boolean) {
        _ui.value = _ui.value.copy(showLogin = show, error = if (show) null else _ui.value.error)
    }

    fun consumeError() {
        _ui.value = _ui.value.copy(error = null)
    }

    fun loadBrowse(order: String = _ui.value.browseOrder) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                busy = true,
                error = null,
                browseOrder = order,
                following = false,
                fiction = null,
            )
            try {
                val works = withContext(Dispatchers.IO) { repo.browse(1, order) }
                _ui.value = _ui.value.copy(works = works, busy = false)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not load Royal Road",
                )
            }
        }
    }

    fun search() {
        val q = _ui.value.query.trim()
        if (q.isEmpty()) {
            loadBrowse()
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, following = false, fiction = null)
            try {
                val works = withContext(Dispatchers.IO) { repo.search(q) }
                _ui.value = _ui.value.copy(works = works, busy = false)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Search failed",
                )
            }
        }
    }

    fun loadFollows() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, following = true, fiction = null)
            try {
                val works = withContext(Dispatchers.IO) { repo.follows() }
                _ui.value = _ui.value.copy(works = works, busy = false)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not load follows",
                )
            }
        }
    }

    fun openUrl() {
        val raw = _ui.value.urlDraft.trim()
        if (raw.isEmpty()) {
            _ui.value = _ui.value.copy(error = "Paste a Royal Road fiction or chapter URL")
            return
        }
        openFictionUrl(raw)
    }

    fun openWork(work: SourceWork) {
        openFictionUrl(work.url)
    }

    fun closeFiction() {
        _ui.value = _ui.value.copy(fiction = null)
    }

    private fun openFictionUrl(url: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                val detail = withContext(Dispatchers.IO) { repo.loadWork(url) }
                _ui.value = _ui.value.copy(
                    fiction = detail,
                    busy = false,
                    urlDraft = url,
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not load fiction",
                )
            }
        }
    }

    fun startReading(actions: LibraryPluginActions, startIndex: Int, queue: Boolean) {
        val detail = _ui.value.fiction ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            actions.setBusy(true)
            try {
                val session = withContext(Dispatchers.IO) {
                    val started = repo.startReading(detail, startIndex)
                    val text = started.chapters.joinToString("\n\n") { ch ->
                        buildString {
                            if (ch.title.isNotBlank()) {
                                append(ch.title)
                                append("\n\n")
                            }
                            append(ch.blocks.joinToString("\n\n") { it.text })
                        }
                    }
                    flow.catalog.upsertPluginBook(
                        bookId = started.bookId,
                        title = started.title,
                        sourceUri = started.fictionUrl,
                        sourceKind = RoyalRoadPlugin.ID,
                        text = text.ifBlank { started.title },
                    ).also { row ->
                        flow.db.progress().upsert(
                            row.copy(
                                chapterIndex = 0,
                                blockIndex = 0,
                                charOffset = 0,
                                readingProgress = 0f,
                            ),
                        )
                    }
                    started
                }
                _ui.value = _ui.value.copy(busy = false)
                actions.setBusy(false)
                if (queue) {
                    actions.queueBook(session.bookId)
                } else {
                    actions.openBook(session.bookId)
                }
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not open fiction",
                )
                actions.setBusy(false)
                actions.showError(t.message ?: "Could not open fiction")
            }
        }
    }

    fun login() {
        val email = _ui.value.emailDraft.trim()
        val password = _ui.value.passwordDraft
        if (email.isEmpty() || password.isEmpty()) {
            _ui.value = _ui.value.copy(error = "Email and password are required")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                withContext(Dispatchers.IO) { repo.login(email, password) }
                _ui.value = _ui.value.copy(
                    busy = false,
                    loggedIn = true,
                    loginEmail = email,
                    passwordDraft = "",
                    showLogin = false,
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Sign in failed",
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.logout() }
            _ui.value = _ui.value.copy(
                loggedIn = false,
                loginEmail = "",
                following = false,
                emailDraft = "",
                passwordDraft = "",
            )
        }
    }
}
