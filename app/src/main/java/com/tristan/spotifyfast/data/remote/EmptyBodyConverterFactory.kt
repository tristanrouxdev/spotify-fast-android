package com.tristan.spotifyfast.data.remote

import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Prevents `EOFException` / `SerializationException` when Spotify endpoints return HTTP 204 or empty bodies.
 */
class EmptyBodyConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> {
        val delegate = retrofit.nextResponseBodyConverter<Any?>(this, type, annotations)
        return Converter<ResponseBody, Any?> { body ->
            if (body.contentLength() == 0L) null else delegate.convert(body)
        }
    }
}
