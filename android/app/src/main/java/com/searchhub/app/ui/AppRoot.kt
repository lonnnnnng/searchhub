@file:OptIn(ExperimentalMaterial3Api::class)

package com.searchhub.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.searchhub.app.ui.captcha.CaptchaHost
import com.searchhub.app.ui.navigation.SearchHubNavHost

@Composable
fun AppRoot(model: AppViewModel = viewModel()) {
    CaptchaHost(
        model = model,
    ) {
        SearchHubNavHost(model = model)
    }
}