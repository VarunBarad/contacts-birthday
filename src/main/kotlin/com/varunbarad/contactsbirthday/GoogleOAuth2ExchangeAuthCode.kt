package com.varunbarad.contactsbirthday

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleOAuth2ExchangeAuthCodeRequest(
    @JsonProperty("client_id") val clientId: String,
    @JsonProperty("client_secret") val clientSecret: String,
    @JsonProperty("code") val code: String,
    @JsonProperty("grant_type") val grantType: String,
    @JsonProperty("redirect_uri") val redirectUri: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleOAuth2ExchangeAuthCodeResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("scope") val scope: String,
    @JsonProperty("token_type") val tokenType: String,
)
