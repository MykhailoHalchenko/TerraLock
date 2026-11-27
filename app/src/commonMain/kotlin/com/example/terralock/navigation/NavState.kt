package com.example.terralock.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class Route {
    data object Auth : Route()
    data object MapHud : Route()
    data object VehicleProfileConfig : Route()
}

data class AppNavState(
    val backStack: List<Route> = listOf(Route.Auth)
) {
    val currentRoute: Route get() = backStack.lastOrNull() ?: Route.Auth
}

sealed interface NavAction {
    data class Push(val route: Route) : NavAction
    data object Pop : NavAction
    data class ReplaceRoot(val route: Route) : NavAction
    data class DeepLink(val uri: String) : NavAction
}

class AppNavReducer {
    private val _state = MutableStateFlow(AppNavState())
    val state: StateFlow<AppNavState> = _state.asStateFlow()

    fun dispatch(action: NavAction) {
        _state.update { current ->
            when (action) {
                is NavAction.Push -> current.copy(backStack = current.backStack + action.route)
                is NavAction.Pop -> current.copy(backStack = current.backStack.dropLast(1).ifEmpty { listOf(Route.Auth) })
                is NavAction.ReplaceRoot -> current.copy(backStack = listOf(action.route))
                is NavAction.DeepLink -> {
                    if (action.uri.contains("terralock://map")) {
                        current.copy(backStack = listOf(Route.MapHud))
                    } else current
                }
            }
        }
    }
}