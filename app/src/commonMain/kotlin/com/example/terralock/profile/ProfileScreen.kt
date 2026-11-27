package com.example.terralock.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.terralock.auth.AuthRepository
import com.example.terralock.navigation.AppNavReducer
import com.example.terralock.navigation.NavAction
import com.example.terralock.navigation.Route
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navReducer: AppNavReducer,
    authRepo: AuthRepository,
    profileRepo: ProfileRepository = remember { ProfileRepository() }
) {
    val currentProfile by profileRepo.currentProfile.collectAsState()
    val scope = rememberCoroutineScope()

    var displayName by remember(currentProfile) { mutableStateOf(currentProfile?.displayName ?: "Commander") }
    var username by remember(currentProfile) { mutableStateOf(currentProfile?.username ?: "tactical_user") }
    var bio by remember(currentProfile) { mutableStateOf(currentProfile?.bio ?: "TerraLock Autonomous Navigator") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Operator Profile") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = displayName.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } },
                label = { Text("Username Handle (@)") },
                prefix = { Text("@") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio / Call sign") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch {
                        val updated = (currentProfile ?: UserProfile()).copy(
                            displayName = displayName,
                            username = username,
                            bio = bio
                        )
                        profileRepo.saveProfile(updated)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Profile")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        authRepo.signOut()
                        navReducer.dispatch(NavAction.ReplaceRoot(Route.Auth))
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign Out")
            }
        }
    }
}