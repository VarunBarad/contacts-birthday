package com.varunbarad.contactsbirthday

import java.time.LocalDate

data class ContactEvent(
    val contactName: String,
    val eventName: String,
    val eventDate: LocalDate,
)
