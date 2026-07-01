package com.backup.telegram.data.remote

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Url

/**
 * يستخدم Url ديناميكي لأن التوكن جزء من الـ path:
 * https://api.telegram.org/bot<TOKEN>/sendPhoto
 * هذا يتيح لنا تبديل التوكن من الإعدادات دون إعادة بناء Retrofit instance.
 */
interface TelegramApiService {

    @Multipart
    @POST
    suspend fun sendPhoto(
        @Url url: String,
        @Part chatId: MultipartBody.Part,
        @Part photo: MultipartBody.Part,
        @Part caption: MultipartBody.Part? = null
    ): Response<ResponseBody>

    @Multipart
    @POST
    suspend fun sendVideo(
        @Url url: String,
        @Part chatId: MultipartBody.Part,
        @Part video: MultipartBody.Part,
        @Part caption: MultipartBody.Part? = null
    ): Response<ResponseBody>

    @Multipart
    @POST
    suspend fun sendDocument(
        @Url url: String,
        @Part chatId: MultipartBody.Part,
        @Part document: MultipartBody.Part,
        @Part caption: MultipartBody.Part? = null
    ): Response<ResponseBody>
}
