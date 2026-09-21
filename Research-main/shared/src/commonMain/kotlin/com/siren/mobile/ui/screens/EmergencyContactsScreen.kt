package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContactEmergency
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.siren.mobile.model.DefaultEmergencyContacts
import com.siren.mobile.model.EmergencyContact
import com.siren.mobile.platform.Platform
import com.siren.mobile.ui.components.Avatar
import com.siren.mobile.ui.components.EmptyState
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.ListRow
import com.siren.mobile.ui.components.Pill
import com.siren.mobile.ui.components.RowDivider
import com.siren.mobile.ui.components.SecondaryButton
import com.siren.mobile.ui.components.SirenField
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenTheme
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.util.initials

@Composable
fun EmergencyContactsScreen(
    contacts: List<EmergencyContact>,
    onAdd: (EmergencyContact) -> Unit,
    onRemove: (String) -> Unit,
    onCall: (String) -> Unit,
    onText: (String) -> Unit,
    onRestoreDefaults: () -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<EmergencyContact?>(null) }

    val missingOfficial = DefaultEmergencyContacts.filterNot { d -> contacts.any { it.id == d.id } }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Layout.screenPadding,
            end = Layout.screenPadding,
            bottom = Space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            ScreenHeader(
                title = "Emergency contacts",
                onBack = onBack,
                trailing = {
                    IconButton(onClick = { adding = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add contact")
                    }
                },
            )
        }

        item {
            InfoBanner(

                "Tap a contact to call or text them yourself. Your phone dials directly, so these numbers work even with no internet.",
                Icons.Filled.Sms,
            )
        }

        if (missingOfficial.isNotEmpty()) {
            item {
                SecondaryButton(
                    text = if (missingOfficial.size == 1) {
                        "Restore 1 official number"
                    } else {
                        "Restore ${missingOfficial.size} official numbers"
                    },
                    onClick = onRestoreDefaults,
                    icon = Icons.Filled.Restore,
                )
            }
        }

        if (contacts.isEmpty()) {
            item {
                EmptyState(
                    title = "No contacts saved",
                    subtitle = "Add at least one guardian or responder so alerts can reach someone offline.",
                    icon = Icons.Filled.ContactEmergency,
                )
            }
        } else {
            item {
                ListGroup {
                    contacts.forEachIndexed { i, contact ->
                        ContactRow(
                            contact = contact,
                            onCall = { onCall(contact.phone) },
                            onText = { onText(contact.phone) },
                            onDelete = { pendingDelete = contact },
                        )
                        if (i < contacts.lastIndex) RowDivider()
                    }
                }
            }
        }
    }

    if (adding) {
        AddContactDialog(
            onDismiss = { adding = false },
            onSave = {
                onAdd(it)
                adding = false
            },
        )
    }

    pendingDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${contact.name}?") },
            text = { Text("They will be removed from your quick-call list. You can add them again at any time.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(contact.id)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ContactRow(
    contact: EmergencyContact,
    onCall: () -> Unit,
    onText: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ListRow(
        title = contact.name,
        subtitle = "${contact.relation} · ${contact.phone}",
        onClick = onCall,
        leading = { Avatar(contact.name.initials(), size = 40.dp) },
        trailing = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (contact.primary) {
                    Pill(
                        "PRIMARY",
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primaryContainer,
                    )
                }
                IconButton(onClick = onCall) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = "Call ${contact.name}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${contact.name}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Send a text") },
                            leadingIcon = { Icon(Icons.Filled.Sms, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onText()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove", color = SirenTheme.status.danger) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = SirenTheme.status.danger,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onSave: (EmergencyContact) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add emergency contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                SirenField(name, { name = it }, "Name")
                SirenField(relation, { relation = it }, "Relation")
                SirenField(phone, { phone = it }, "Phone", keyboardType = KeyboardType.Phone)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && phone.isNotBlank(),
                onClick = {
                    onSave(
                        EmergencyContact(
                            id = "c${Platform.services.nowMillis()}",
                            name = name.trim(),
                            relation = relation.trim().ifBlank { "Contact" },
                            phone = phone.trim(),
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
