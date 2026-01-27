package com.mobilellama.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilellama.R
import com.mobilellama.data.model.AiModel
import com.mobilellama.viewmodel.DownloadViewModel

@Composable
fun Sidebar(
    onModelSelected: (AiModel) -> Unit,
    onManageModels: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels = viewModel.availableModels

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.infx_logo),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Neural Engines",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // Model List
            Text(
                text = "AVAILABLE MODELS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(availableModels) { model ->
                    NavigationDrawerItem(
                        label = { Text(model.name) },
                        selected = model == selectedModel,
                        onClick = {
                            viewModel.selectModel(model)
                            onModelSelected(model)
                        },
                        icon = {
                            if (model == selectedModel) {
                                Icon(painterResource(id = R.drawable.infx_logo), contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        },
                        badge = {
                            // Optional: Show status dot?
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            
            // Footer
            NavigationDrawerItem(
                label = { Text("Manage Models") },
                selected = false,
                onClick = onManageModels,
                icon = { Icon(Icons.Default.Settings, contentDescription = null) }
            )
        }
    }
}
