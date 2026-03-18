package es.upm.etsisi.mad.bioquiet.data.remote

import android.util.Log
import es.upm.etsisi.mad.bioquiet.data.remote.mapper.ZepaMapper
import es.upm.etsisi.mad.bioquiet.model.Zepa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object ApiClient {
    private const val LOG_TAG = "ApiClient"
    private const val BASE_URL: String = "https://bioquiet-backend-production.up.railway.app"
    private const val CONNECT_TIMEOUT_MS: Int = 15000
    private const val READ_TIMEOUT_MS: Int = 5000

    suspend fun fetchNearsZepa(
        lonWest: Double,
        latSouth: Double,
        lonEast: Double,
        latNorth: Double
    ): List<Zepa> {
        return withContext(Dispatchers.IO) {
            val url = URL(
                "$BASE_URL/api/v1/zones/zepa" +
                        "?lonWest=${lonWest}" +
                        "&latSouth=${latSouth}" +
                        "&lonEast=${lonEast}" +
                        "&latNorth=${latNorth}"
            )
            Log.d(LOG_TAG, "Fetching ZEPAs: $url")

            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val responseCode = connection.responseCode
            Log.d(LOG_TAG, "Response code: $responseCode")

            val json = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val result = ZepaMapper.toZepa(json)
            Log.d(LOG_TAG, "Fetched ${result.size} ZEPAs")
            result
        }
    }
}
