package com.bundel.sms

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bundel.sms.utils.Contact
import com.bundel.sms.utils.FileParserHelper
import com.bundel.sms.utils.SmsManagerHelper
import com.bundel.sms.viewmodel.CampaignHistory
import com.bundel.sms.viewmodel.SmsViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: SmsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = dynamicLightColorScheme(this)) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(this, viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(context: android.content.Context, viewModel: SmsViewModel) {
    var currentScreen by remember { mutableStateOf("MAIN") }

    BackHandler(enabled = currentScreen == "REPORT") {
        currentScreen = "MAIN"
    }

    if (currentScreen == "MAIN") {
        ModernMainScreen(context, viewModel, onReportClick = { currentScreen = "REPORT" })
    } else {
        ReportScreen(viewModel, onBackPressed = { currentScreen = "MAIN" })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernMainScreen(context: android.content.Context, viewModel: SmsViewModel, onReportClick: () -> Unit) {
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS
            )
        )
    }

    val smsHelper = remember { SmsManagerHelper(context) }
    val availableSims = remember { smsHelper.getAvailableSims() }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            Toast.makeText(context, "CSV ফাইল প্রসেস হচ্ছে...", Toast.LENGTH_SHORT).show()
            val parsedContacts = FileParserHelper.parseCsv(context, it)
            if (parsedContacts.isNotEmpty()) {
                viewModel.loadContacts(parsedContacts)
            }
        }
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let { contactUri ->
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(contactUri, null, null, null, null)
            
            if (cursor != null && cursor.moveToFirst()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                
                val phones = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + id, null, null
                )
                
                var phoneNumber = ""
                if (phones != null && phones.moveToFirst()) {
                    phoneNumber = phones.getString(phones.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    phones.close()
                }
                cursor.close()

                if (phoneNumber.isNotEmpty()) {
                    val cleanPhone = phoneNumber.replace(Regex("[^0-9+]"), "")
                    val newContact = Contact(
                        id = (System.currentTimeMillis() % 10000).toInt(),
                        name = name,
                        phoneNumber = cleanPhone
                    )
                    viewModel.contacts.add(newContact)
                }
            }
        }
    }

    LaunchedEffect(availableSims) {
        if (availableSims.isNotEmpty() && viewModel.selectedSimId.value == -1) {
            viewModel.selectedSimId.value = availableSims[0].subscriptionId
        }
    }

    Scaffold(
        topBar = { 
            CenterAlignedTopAppBar(
                title = { Text("Bulk SMS Pro", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onReportClick) {
                        // Assessment এর বদলে List ব্যবহার করা হয়েছে
                        Icon(Icons.Default.List, contentDescription = "Report", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            ) 
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ImportCardButton(
                    onClick = { csvLauncher.launch(arrayOf("*/*")) },
                    icon = Icons.Default.Add, label = "CSV আপলোড", description = "(RAMPAL.csv)", modifier = Modifier.weight(1f)
                )
                ImportCardButton(
                    onClick = { contactPickerLauncher.launch(null) },
                    icon = Icons.Default.Person, label = "কন্টাক্ট থেকে", description = "(এক এক করে)", modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (availableSims.size > 1) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("সিম কার্ড সিলেক্ট করুন:", style = MaterialTheme.typography.labelLarge)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            availableSims.forEach { simInfo ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                                    RadioButton(selected = viewModel.selectedSimId.value == simInfo.subscriptionId, onClick = { viewModel.selectedSimId.value = simInfo.subscriptionId })
                                    Text("${simInfo.carrierName} (SIM ${simInfo.simSlotIndex + 1})")
                                }
                            }
                        }
                    }
                }
            }

            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("কন্টাক্ট সংখ্যা: ", style = MaterialTheme.typography.labelLarge)
                    Text("${viewModel.contacts.count { it.isSelected }} / ${viewModel.contacts.size}", style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary))
                }
            }
            
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(viewModel.contacts) { contact ->
                    ListItem(
                        headlineContent = { Text(contact.name, style = MaterialTheme.typography.titleSmall) },
                        supportingContent = { Text(contact.phoneNumber, style = MaterialTheme.typography.bodyMedium) },
                        leadingContent = { Checkbox(checked = contact.isSelected, onCheckedChange = { viewModel.toggleContactSelection(contact.id) }) },
                        trailingContent = { IconButton(onClick = { viewModel.deleteContact(contact.id) }) { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) } }
                    )
                    HorizontalDivider()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.messageText.value, onValueChange = { viewModel.messageText.value = it },
                label = { Text("মেসেজ লিখুন (নামের জায়গায় {Name} দিন)") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5, shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (viewModel.isSending.value) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LinearProgressIndicator(progress = viewModel.currentProgress.value, modifier = Modifier.fillMaxWidth())
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("প্রেরণ হচ্ছে: ${viewModel.sentCount.value}", color = Color(0xFF4CAF50))
                            Text("ব্যর্থ: ${viewModel.failedCount.value}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.startBulkSmsCampaign() },
                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(CircleShape),
                    enabled = viewModel.contacts.isNotEmpty() && viewModel.messageText.value.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("SMS পাঠানো শুরু করুন", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        
        if (viewModel.showCompletionDialog.value) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissCompletionDialog() },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp)) },
                title = { Text("এসএমএস রিপোর্ট") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("আপনার মেসেজ পাঠানোর কাজ সম্পন্ন হয়েছে।")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("ডেলিভার্ড: ${viewModel.sentCount.value}", color = Color(0xFF4CAF50), style = MaterialTheme.typography.titleMedium)
                        Text("নট সেন্ড: ${viewModel.failedCount.value}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    }
                },
                confirmButton = { Button(onClick = { viewModel.dismissCompletionDialog() }) { Text("ঠিক আছে") } }
            )
        }
    }
}

// --- রিপোর্ট স্ক্রিন ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: SmsViewModel, onBackPressed: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ক্যাম্পেইন রিপোর্ট") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        if (viewModel.historyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো রিপোর্ট পাওয়া যায়নি", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
                items(viewModel.historyList) { campaign ->
                    CampaignReportCard(campaign)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun CampaignReportCard(campaign: CampaignHistory) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // DateRange এর বদলে Info আইকন দেওয়া হয়েছে
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${campaign.date} ; ${campaign.time}", style = MaterialTheme.typography.titleMedium)
                }
                Icon(
                    // ExpandLess/ExpandMore এর বদলে KeyboardArrowUp/Down ব্যবহার করা হয়েছে
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    campaign.records.forEach { record ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(record.phone, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = record.status, 
                                color = if (record.status == "Delivered") Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCardButton(onClick: () -> Unit, icon: ImageVector, label: String, description: String, modifier: Modifier = Modifier) {
    OutlinedCard(
        onClick = onClick, modifier = modifier.height(100.dp), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary))
            Text(description, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}
