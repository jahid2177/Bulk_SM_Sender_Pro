package com.bundel.sms.viewmodel

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bundel.sms.utils.Contact
import com.bundel.sms.utils.SmsManagerHelper
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// হিস্ট্রি সেভ করার জন্য ডাটা ক্লাস
data class SmsRecord(val phone: String, val status: String)
data class CampaignHistory(val date: String, val time: String, val records: List<SmsRecord>)

class SmsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val smsHelper = SmsManagerHelper(application)
    
    val contacts = mutableStateListOf<Contact>()
    val messageText = mutableStateOf("")
    val selectedSimId = mutableStateOf(-1)
    
    val isSending = mutableStateOf(false)
    val sentCount = mutableStateOf(0)
    val failedCount = mutableStateOf(0)
    val currentProgress = mutableStateOf(0f)
    val showCompletionDialog = mutableStateOf(false)

    // বর্তমান ক্যাম্পেইনের হিস্ট্রি
    private val currentCampaignRecords = mutableListOf<SmsRecord>()
    
    // সেভ করা সব হিস্ট্রি
    val historyList = mutableStateListOf<CampaignHistory>()

    init {
        loadHistory()
    }

    fun loadContacts(newContacts: List<Contact>) {
        contacts.clear()
        contacts.addAll(newContacts)
    }

    fun toggleContactSelection(contactId: Int) {
        val index = contacts.indexOfFirst { it.id == contactId }
        if (index != -1) {
            val contact = contacts[index]
            contacts[index] = contact.copy(isSelected = !contact.isSelected)
        }
    }

    fun deleteContact(contactId: Int) {
        contacts.removeAll { it.id == contactId }
    }

    fun dismissCompletionDialog() {
        showCompletionDialog.value = false
    }

    fun startBulkSmsCampaign() {
        val context = getApplication<Application>().applicationContext
        val selectedContacts = contacts.filter { it.isSelected }
        if (selectedContacts.isEmpty()) {
            Toast.makeText(context, "কোনো কন্টাক্ট সিলেক্ট করা নেই!", Toast.LENGTH_SHORT).show()
            return
        }
        if (messageText.value.isBlank()) {
            Toast.makeText(context, "মেসেজ বক্স খালি!", Toast.LENGTH_SHORT).show()
            return
        }

        val simId = if (selectedSimId.value != -1) selectedSimId.value else -1
        
        isSending.value = true
        sentCount.value = 0
        failedCount.value = 0
        currentProgress.value = 0f
        currentCampaignRecords.clear()

        Toast.makeText(context, "SMS পাঠানো শুরু হচ্ছে...", Toast.LENGTH_SHORT).show()

        viewModelScope.launch {
            smsHelper.sendBulkSms(
                contacts = selectedContacts,
                messageTemplate = messageText.value,
                subscriptionId = simId
            ) { sent, failed, currentIndex, phone, isSuccess ->
                sentCount.value = sent
                failedCount.value = failed
                currentProgress.value = currentIndex.toFloat() / selectedContacts.size.toFloat()
                
                // রেকর্ড সেভ করা
                val statusStr = if (isSuccess) "Delivered" else "Not Sent"
                currentCampaignRecords.add(SmsRecord(phone, statusStr))
                
                if (currentIndex == selectedContacts.size) {
                    isSending.value = false 
                    saveCampaignHistory() // ক্যাম্পেইন শেষে হিস্ট্রি সেভ করবে
                    showCompletionDialog.value = true
                }
            }
        }
    }

    private fun saveCampaignHistory() {
        val context = getApplication<Application>().applicationContext
        val prefs = context.getSharedPreferences("sms_history_prefs", Context.MODE_PRIVATE)
        val historyString = prefs.getString("history_data", "[]")
        
        try {
            val jsonArray = JSONArray(historyString)
            val campObj = JSONObject()
            
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val currentDate = Date()
            
            campObj.put("date", dateFormat.format(currentDate))
            campObj.put("time", timeFormat.format(currentDate))

            val recArray = JSONArray()
            currentCampaignRecords.forEach {
                val recObj = JSONObject()
                recObj.put("phone", it.phone)
                recObj.put("status", it.status)
                recArray.put(recObj)
            }
            campObj.put("records", recArray)
            jsonArray.put(campObj) // নতুনটা যুক্ত হলো

            prefs.edit().putString("history_data", jsonArray.toString()).apply()
            loadHistory() // আপডেট করা হিস্ট্রি রিলোড
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun loadHistory() {
        val context = getApplication<Application>().applicationContext
        val prefs = context.getSharedPreferences("sms_history_prefs", Context.MODE_PRIVATE)
        val historyString = prefs.getString("history_data", "[]")
        
        historyList.clear()
        try {
            val jsonArray = JSONArray(historyString)
            for (i in jsonArray.length() - 1 downTo 0) { // লেটেস্টটা আগে দেখাবে
                val campObj = jsonArray.getJSONObject(i)
                val date = campObj.getString("date")
                val time = campObj.getString("time")
                val recArray = campObj.getJSONArray("records")
                
                val records = mutableListOf<SmsRecord>()
                for (j in 0 until recArray.length()) {
                    val recObj = recArray.getJSONObject(j)
                    records.add(SmsRecord(recObj.getString("phone"), recObj.getString("status")))
                }
                historyList.add(CampaignHistory(date, time, records))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}
