package es.upm.etsisi.mad.bioquiet.data.remote

import es.upm.etsisi.mad.bioquiet.model.Zepa
import es.upm.etsisi.mad.bioquiet.data.remote.mapper.ZepaMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object ApiClient {
    const val BASE_URL: String = "https://bioquiet-backend-production.up.railway.app/"
    const val CONNECT_TIMEOUT_MS: Int = 15000
    const val READ_TIMEOUT_MS: Int = 5000

    suspend fun fetchNearsZepa(lonWest: Double, latSouth: Double, lonEast: Double, latNorth: Double): List<Zepa> {
        return withContext(Dispatchers.IO) {
            val url = URL(
                BASE_URL +
                        "/api/v1/zones/zepa" +
                        "?lonWest=${lonWest}" +
                        "&latSouth=${latSouth}" +
                        "&lonEast=${lonEast}" +
                        "&latNorth=${latNorth}"
            )

            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val json = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            ZepaMapper.toZepa(json)
        }
    }
}