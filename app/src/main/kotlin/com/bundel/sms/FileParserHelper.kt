package com.bundel.sms.utils

import android.content.Context
import android.net.Uri
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader

object FileParserHelper {
    fun parseCsv(context: Context, uri: Uri): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val duplicates = mutableSetOf<String>()
        
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            val headerLine = reader.readLine() ?: return emptyList()
            val headers = headerLine.split(",").map { it.trim().lowercase() }
            
            // RAMPAL.csv এর কলাম অনুযায়ী ইনডেক্স খোঁজা
            val nameIdx = headers.indexOfFirst { it.contains("name") }
            val phoneIdx = headers.indexOfFirst { it.contains("mobile") || it.contains("phone") || it.contains("number") }

            val finalNameIdx = if (nameIdx != -1) nameIdx else 0
            val finalPhoneIdx = if (phoneIdx != -1) phoneIdx else 3 // আপনার ফাইলে এটি ৪র্থ কলামে

            var line: String?
            var idCounter = 1
            
            while (reader.readLine().also { line = it } != null) {
                val tokens = line?.split(",") ?: continue
                if (tokens.size > finalPhoneIdx) {
                    val name = tokens[finalNameIdx].trim().removeSurrounding("\"")
                    val phone = tokens[finalPhoneIdx].trim().removeSurrounding("\"")
                        .replace(Regex("[^0-9+]"), "") 
                    
                    if (phone.isNotEmpty() && !duplicates.contains(phone)) {
                        duplicates.add(phone)
                        contacts.add(Contact(idCounter++, name, phone))
                    }
                }
            }
            reader.close()
            Toast.makeText(context, "${contacts.size} টি নম্বর পাওয়া গেছে", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
        return contacts
    }
}
