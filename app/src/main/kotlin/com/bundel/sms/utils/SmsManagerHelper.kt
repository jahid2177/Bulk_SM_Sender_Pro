package com.bundel.sms.utils

import android.content.Context
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class SmsManagerHelper(private val context: Context) {

    fun getAvailableSims(): List<SubscriptionInfo> {
        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            subscriptionManager.activeSubscriptionInfoList ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun sendBulkSms(
        contacts: List<Contact>,
        messageTemplate: String,
        subscriptionId: Int,
        onProgress: (sent: Int, failed: Int, currentIndex: Int, phone: String, isSuccess: Boolean) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val smsManager: SmsManager = try {
                if (subscriptionId != -1 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                } else {
                    SmsManager.getDefault()
                }
            } catch (e: Exception) {
                SmsManager.getDefault()
            }

            var sentCount = 0
            var failedCount = 0

            contacts.forEachIndexed { index, contact ->
                var isSuccess = false
                try {
                    val personalizedMessage = messageTemplate.replace("{Name}", contact.name)
                    val parts = smsManager.divideMessage(personalizedMessage)
                    if (parts.size > 1) {
                        smsManager.sendMultipartTextMessage(contact.phoneNumber, null, parts, null, null)
                    } else {
                        smsManager.sendTextMessage(contact.phoneNumber, null, personalizedMessage, null, null)
                    }
                    sentCount++
                    isSuccess = true
                } catch (e: Exception) {
                    failedCount++
                }

                delay(500) // স্প্যাম ব্লক এড়াতে ডিলে

                withContext(Dispatchers.Main) {
                    onProgress(sentCount, failedCount, index + 1, contact.phoneNumber, isSuccess)
                }
            }
        }
    }
}
