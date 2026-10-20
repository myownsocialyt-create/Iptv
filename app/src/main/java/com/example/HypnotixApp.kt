package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.ad.AdManager
import com.example.ad.InStreamAdManager
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HypnotixApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AdManager.initialize(this)
        InStreamAdManager.preloadAdMobNativeAd(this)
    }

    override fun newImageLoader(): ImageLoader {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }

        val okHttpBuilder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "image/webp,image/apng,image/png,image/jpeg,image/*,*/*;q=0.8")
                    .build()
                chain.proceed(request)
            }

        // Allow self-signed / regional CDN certificates for IPTV channel logos
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            okHttpBuilder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            okHttpBuilder.hostnameVerifier { _, _ -> true }
        } catch (_: Exception) {}

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpBuilder.build())
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("iptv_logo_cache"))
                    .maxSizeBytes(80L * 1024 * 1024) // 80 MB persistent logo cache
                    .build()
            }
            .crossfade(180)
            .respectCacheHeaders(false)
            .build()
    }
}
