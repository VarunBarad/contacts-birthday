package com.varunbarad.contactsbirthday

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.property.Description
import biweekly.property.RecurrenceRule
import biweekly.property.Summary
import biweekly.util.Frequency
import biweekly.util.Recurrence
import com.google.api.client.auth.oauth2.BearerToken
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.people.v1.PeopleService
import com.google.api.services.people.v1.model.Date
import com.google.api.services.people.v1.model.Person
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestTemplate
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.web.servlet.view.RedirectView
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import java.util.Date as JavaDate

@Controller
class HtmlController(
    val googleConfiguration: GoogleConfiguration,
) {
    companion object {
        private const val GOOGLE_AUTH_GRANT_TYPE = "authorization_code"
        private const val GOOGLE_AUTH_STATE = "contacts_calendar_google"
        private val GOOGLE_AUTH_SCOPES = listOf(
            "https://www.googleapis.com/auth/contacts.readonly",
        )
    }

    private val logger = LoggerFactory.getLogger(HtmlController::class.java)

    @GetMapping("/")
    fun blog(model: Model): String {
        val queryParameters = mapOf(
            "redirect_uri" to googleConfiguration.redirectUri,
            "response_type" to "code",
            "client_id" to googleConfiguration.clientId,
            "scope" to GOOGLE_AUTH_SCOPES.joinToString(separator = " "),
            "include_granted_scope" to "true",
            "access_type" to "online",
            "state" to GOOGLE_AUTH_STATE,
            "prompt" to "consent",
        )
        val encodedQueryParameters = queryParameters.map { (key, value) ->
            val encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8)
            "$key=$encodedValue"
        }.joinToString(separator = "&")

        model["GOOGLE_AUTH_URL"] = "https://accounts.google.com/o/oauth2/v2/auth?$encodedQueryParameters"

        return "index"
    }

    @GetMapping("/auth")
    fun auth(
        model: Model,
        @RequestParam(name = "state", required = true) stateParam: String,
        @RequestParam(name = "error", required = false) errorParam: String?,
        @RequestParam(name = "code", required = false) codeParam: String?,
        @RequestParam(name = "scope", required = false, defaultValue = "") scopeParam: String,
        redirectAttributes: RedirectAttributes,
    ): RedirectView {
        if (stateParam != GOOGLE_AUTH_STATE) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "`state` parameter returned by Google does not match what was supplied",
            )
            return RedirectView("authError")
        }

        if (errorParam != null) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                errorParam,
            )
            return RedirectView("authError")
        }

        if (codeParam != null) {
            val allRequiredScopesAreGranted = scopeParam.split(" ")
                .map { URLDecoder.decode(it.trim(), StandardCharsets.UTF_8).trim() }
                .containsAll(GOOGLE_AUTH_SCOPES)

            if (allRequiredScopesAreGranted.not()) {
                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Please grant all required permissions for this tool to function.",
                )
                return RedirectView("authError")
            }

            val accessTokenResponse = getAccessTokenFromGoogle(code = codeParam)

            if (accessTokenResponse == null) {
                redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Error exchanging auth-code for access-token with Google",
                )
                return RedirectView("authError")
            }

            redirectAttributes.addFlashAttribute(
                "accessToken",
                accessTokenResponse,
            )
            return RedirectView("success")
        }

        redirectAttributes.addFlashAttribute(
            "errorMessage",
            "Unknown error",
        )
        return RedirectView("authError")
    }

    private fun getAccessTokenFromGoogle(code: String): GoogleOAuth2ExchangeAuthCodeResponse? {
        val requestBody = GoogleOAuth2ExchangeAuthCodeRequest(
            clientId = googleConfiguration.clientId,
            clientSecret = googleConfiguration.clientSecret,
            code = code,
            grantType = GOOGLE_AUTH_GRANT_TYPE,
            redirectUri = googleConfiguration.redirectUri,
        )
        val restTemplate = RestTemplate()
        val response = restTemplate.postForEntity(
            "https://oauth2.googleapis.com/token",
            requestBody,
            GoogleOAuth2ExchangeAuthCodeResponse::class.java,
        )

        return response.body
    }

    @GetMapping("/success")
    fun authSuccess(
        model: Model,
    ): String {
        val accessTokenAttribute = model.getAttribute("accessToken")

        if (accessTokenAttribute != null) {
            val contacts = fetchContactsFromGoogle(accessTokenAttribute as GoogleOAuth2ExchangeAuthCodeResponse)

            val contactEvents = contacts.filter { it.containsKey("birthdays") }
                .flatMap { person ->
                    val contactName = person.names.first().displayName

                    person.birthdays.map { birthday ->
                        ContactEvent(
                            contactName = contactName,
                            eventName = birthday.text ?: "🎂 Birthday",
                            eventDate = birthday.date.toLocalDate(),
                        )
                    }
                }

            val calendarFeed = generateCalendarFeedForEvents(events = contactEvents)

            model["calendarContents"] = Base64.getEncoder().encodeToString(calendarFeed.encodeToByteArray())
            model["today"] = LocalDate.now().toString()

            return "success"
        } else {
            return "redirect:/"
        }
    }

    private fun fetchContactsFromGoogle(accessToken: GoogleOAuth2ExchangeAuthCodeResponse): List<Person> {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val peopleService = PeopleService.Builder(
            httpTransport,
            GsonFactory.getDefaultInstance(),
            Credential.Builder(BearerToken.authorizationHeaderAccessMethod()).build().setAccessToken(accessToken.accessToken),
        ).setApplicationName(googleConfiguration.applicationName)
            .build()

        val contacts = mutableListOf<Person>()

        var pageToken: String? = null
        do {
            val response = peopleService.people()
                .connections()
                .list("people/me")
                .setPageSize(1000)
                .setPersonFields("names,birthdays")
                .setPageToken(pageToken)
                .execute()
            contacts.addAll(response.connections)
            pageToken = response.nextPageToken
        } while (pageToken != null)

        return contacts
    }

    private fun generateCalendarFeedForEvents(
        events: List<ContactEvent>,
        timezone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val calendarEvents = events.map { event ->
            VEvent().apply {
                summary = Summary(event.contactName)
                description = Description(event.eventName)
                setDateStart(event.eventDate.toJavaDate(timezone = timezone), false)
                recurrenceRule = RecurrenceRule(Recurrence.Builder(Frequency.YEARLY).build())
            }
        }

        val iCal = ICalendar()

        val calendarName = "Contacts Calendar"
        iCal.setName(calendarName)
        iCal.addExperimentalProperty("X-WR_CALNAME", calendarName)

        calendarEvents.forEach { iCal.addEvent(it) }

        return Biweekly.write(iCal).go()
    }

    @GetMapping("/authError")
    fun authError(
        model: Model,
        @ModelAttribute("errorMessage") errorMessage: String,
    ): String {
        model["errorMessage"] = errorMessage
        return "authError"
    }



}

private fun Date.toLocalDate(): LocalDate {
    return LocalDate.of(year ?: 0, month, day)
}

private fun LocalDate.toJavaDate(timezone: ZoneId): JavaDate {
    return JavaDate.from(this.atStartOfDay(timezone).toInstant())
}
