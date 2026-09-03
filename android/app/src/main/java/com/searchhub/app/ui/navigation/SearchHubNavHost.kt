package com.searchhub.app.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.searchhub.app.model.SearchResult
import com.searchhub.app.ui.AppViewModel
import com.searchhub.app.ui.search.DetailScreen
import com.searchhub.app.ui.search.SearchScreen
import com.searchhub.app.ui.settings.SettingsScreen

object Routes {
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DETAIL = "detail"
}

@Composable
fun SearchHubNavHost(
    model: AppViewModel,
    navHostController: androidx.navigation.NavHostController = rememberNavController(),
) {
    NavHost(navController = navHostController, startDestination = Routes.SEARCH) {
        composable(Routes.SEARCH) {
            SearchScreen(
                model = model,
                onOpenSettings = { navHostController.navigate(Routes.SETTINGS) },
                onOpenDetail = { item ->
                    navHostController.navigate("${Routes.DETAIL}/${java.net.URLEncoder.encode(item.detailUrl, "UTF-8")}/${java.net.URLEncoder.encode(item.sourceSite, "UTF-8")}")
                },
            )
        }
        composable(Routes.DETAIL + "/{url}/{site}") { backStackEntry ->
            val url = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("url") ?: "", "UTF-8")
            val site = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("site") ?: "", "UTF-8")
            DetailScreen(url = url, site = site, model = model, onBack = { navHostController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(model = model, onBack = { navHostController.popBackStack() })
        }
    }
}