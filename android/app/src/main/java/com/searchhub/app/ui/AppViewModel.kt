package com.searchhub.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.searchhub.app.data.CaptchaFlow
import com.searchhub.app.data.ConfigStore
import com.searchhub.app.data.HttpEngine
import com.searchhub.app.data.SearchRepository
import com.searchhub.app.data.SiteConfig
import com.searchhub.app.model.CaptchaAnswer
import com.searchhub.app.model.CaptchaRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val engine = HttpEngine()

    private val captchaEvent = MutableSharedFlow<CaptchaRequest>(extraBufferCapacity = 4)
    @Volatile private var pendingAnswer: CompletableDeferred<CaptchaAnswer>? = null

    val captchaRequests = captchaEvent.asSharedFlow()

    val captchaFlow = CaptchaFlow { req ->
        val deferred = CompletableDeferred<CaptchaAnswer>()
        pendingAnswer = deferred
        captchaEvent.tryEmit(req)
        deferred.await()
    }

    val repository = SearchRepository(engine, captchaFlow)

    private val _sites = MutableStateFlow<List<SiteConfig>>(emptyList())
    val sites = _sites.asStateFlow()

    init {
        loadConfig()
    }

    /** UI 提交验证码答案,通知等待方 */
    fun submitCaptchaAnswer(answer: CaptchaAnswer) {
        pendingAnswer?.complete(answer)
        pendingAnswer = null
    }

    fun loadConfig() {
        val sites = ConfigStore.load(getApplication())
        _sites.value = sites
        repository.rebuild(sites)
    }

    fun saveSites(newSites: List<SiteConfig>) {
        _sites.value = newSites
        ConfigStore.save(getApplication(), newSites)
        repository.rebuild(newSites)
    }

    fun resetConfig() {
        pendingAnswer?.complete(CaptchaAnswer(CaptchaAnswer.CANCEL))
        pendingAnswer = null
        ConfigStore.reset(getApplication())
        loadConfig()
    }
}
