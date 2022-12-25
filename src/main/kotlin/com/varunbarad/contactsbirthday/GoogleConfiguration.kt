package com.varunbarad.contactsbirthday

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("contacts-calendar.google")
class GoogleConfiguration {
    lateinit var applicationName: String
    lateinit var clientId: String
    lateinit var clientSecret: String
    lateinit var redirectUri: String
}
