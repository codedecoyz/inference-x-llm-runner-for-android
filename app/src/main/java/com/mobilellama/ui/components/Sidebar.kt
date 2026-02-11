package com.mobilellama.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilellama.R
// Reverting HorizontalDivider to Divider if M3 version is older, or adding import if available. 
// Standard M3 uses Divider in older versions, HorizontalDivider in newer. 
// Safest is to use Divider or check version. Given error, let's assume Divider.
import androidx.compose.material3.Divider // Use Divider for compatibility
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
        drawerContainerColor = MaterialTheme.colorScheme.surface, // NeonSurface (#240046)
        drawerContentColor = MaterialTheme.colorScheme.onBackground
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
                    tint = MaterialTheme.colorScheme.tertiary // NeonAccent
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Neural Engines",
                    style = MaterialTheme.typography.headlineSmall, // Larger header
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground // NeonTextMain
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // Model List
            Text(
                text = "AVAILABLE MODELS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, // NeonTextSecondary
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableModels) { model ->
                    val isSelected = model == selectedModel
                    NavigationDrawerItem(
                        label = { 
                            Text(
                                text = model.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        },
                        selected = isSelected,
                        onClick = {
                            viewModel.selectModel(model)
                            onModelSelected(model)
                        },
                        icon = {
                            if (isSelected) {
                                Icon(
                                    painter = painterResource(id = R.drawable.infx_logo), 
                                    contentDescription = null, 
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unselectedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.tertiary, // Neon Glow
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp) // Default drawer item shape
                                    )
                                } else Modifier
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Footer
            NavigationDrawerItem(
                label = { Text("Manage Models", color = MaterialTheme.colorScheme.onBackground) },
                selected = false,
                onClick = onManageModels,
                icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) }
            )
        }
    }
}
