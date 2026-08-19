package com.bundel.sms.utils

// এই ক্লাসটি কন্টাক্টের নাম, ফোন নম্বর এবং সে সিলেক্টেড কি না তা মনে রাখে
data class Contact(
    val id: Int,
    val name: String,
    val phoneNumber: String,
    var isSelected: Boolean = true // ডিফল্টভাবে সব কন্টাক্ট সিলেক্টেড থাকবে
)
