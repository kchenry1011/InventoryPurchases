package com.kevin.inventorypurchases.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kevin.inventorypurchases.ui.form.FormScreen
import com.kevin.inventorypurchases.ui.form.FormViewModel
import com.kevin.inventorypurchases.ui.list.ListScreen

object Routes {
    const val FORM = "form"
    const val LIST = "list"
}

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.FORM, modifier = modifier) {
        composable(Routes.FORM) {
            val vm: FormViewModel = viewModel(factory = FormViewModel.Factory)
            FormScreen(
                state = vm.state,
                onIntent = vm::onIntent,
                navigateToList = { nav.navigate(Routes.LIST) }
            )
        }
        composable(Routes.LIST) {
            ListScreen(onBack = { nav.popBackStack() })
        }
    }
}
