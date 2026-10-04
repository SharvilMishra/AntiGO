package com.sharvil.antigo.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import okhttp3.OkHttpClient

@JsonClass(generateAdapter = true)
data class ConversationDto(val id: String, val title: String, val avatarUrl: String?, val updatedAt: Long)

interface MessagingApi

object ApiClient {
    fun create(baseUrl: String, client: OkHttpClient = OkHttpClient()): MessagingApi {
        require(baseUrl.startsWith("https://")) { "Service URL must use HTTPS" }
        return Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(MoshiConverterFactory.create()).build()
            .create(MessagingApi::class.java)
    }
}
