package com.lumiform.formviewer.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lumiform.formviewer.R
import com.lumiform.formviewer.ui.theme.FormTheme

/** Display preferences for the form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsSheet(
    showItemIds: Boolean,
    onShowItemIdsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FormTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                modifier = Modifier.semantics { heading() }.padding(bottom = 16.dp),
            )
            // The whole row toggles, so the target is large and TalkBack reads label, hint and state together.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = showItemIds, role = Role.Switch, onValueChange = onShowItemIdsChange)
                        .padding(vertical = 8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.settings_show_item_ids),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text,
                    )
                    Text(
                        stringResource(R.string.settings_show_item_ids_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                }
                Switch(
                    checked = showItemIds,
                    onCheckedChange = null,
                    colors =
                        SwitchDefaults.colors(
                            checkedTrackColor = colors.selection,
                            checkedThumbColor = colors.onSelection,
                        ),
                )
            }
        }
    }
}
