package es.upm.etsisi.mad.bioquiet.data.remote.mapper

import es.upm.etsisi.mad.bioquiet.model.GeoJsonGeometry
import es.upm.etsisi.mad.bioquiet.model.Habitat
import es.upm.etsisi.mad.bioquiet.model.Impact
import es.upm.etsisi.mad.bioquiet.model.Management
import es.upm.etsisi.mad.bioquiet.model.NoiseThresholds
import es.upm.etsisi.mad.bioquiet.model.Species
import es.upm.etsisi.mad.bioquiet.model.Zepa
import org.json.JSONArray
import org.json.JSONObject

object ZepaMapper {
    private fun parseHabitats(arr: JSONArray?): List<Habitat> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Habitat(
                code = o.optString("code").ifEmpty { null },
                description = o.optString("description").ifEmpty { null },
                priority = if (o.has("priority")) o.getBoolean("priority") else null,
                coverHa = o.optDouble("cover_ha").takeUnless { it.isNaN() },
                representativity = o.optString("representativity").ifEmpty { null },
                conservation = o.optString("conservation").ifEmpty { null },
                globalAssessment = o.optString("global_assessment").ifEmpty { null }
            )
        }
    }

    private fun parseSpecies(arr: JSONArray?): List<Species> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Species(
                code = o.optString("code").ifEmpty { null },
                name = o.optString("name").ifEmpty { null },
                group = o.optString("group").ifEmpty { null },
                populationType = o.optString("population_type").ifEmpty { null },
                abundance = o.optString("abundance").ifEmpty { null },
                conservation = o.optString("conservation").ifEmpty { null },
                global = o.optString("global").ifEmpty { null }
            )
        }
    }

    private fun parseImpacts(arr: JSONArray?): List<Impact> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Impact(
                code = o.optString("code").ifEmpty { null },
                description = o.optString("description").ifEmpty { null },
                intensity = o.optString("intensity").ifEmpty { null },
                occurrence = o.optString("occurrence").ifEmpty { null },
                type = o.optString("type").ifEmpty { null }
            )
        }
    }

    private fun parseManagement(arr: JSONArray?): List<Management> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Management(
                orgName = o.optString("org_name").ifEmpty { null },
                orgEmail = o.optString("org_email").ifEmpty { null },
                planUrl = o.optString("plan_url").ifEmpty { null },
                measures = o.optString("measures").ifEmpty { null }
            )
        }
    }

    private fun parseGeometry(obj: JSONObject): GeoJsonGeometry {
        return GeoJsonGeometry(
            type = obj.getString("type"),
            coordinates = obj.getJSONArray("coordinates")
        )
    }

    private fun parseZepa(obj: JSONObject): Zepa {
        val noise = obj.getJSONObject("noise_thresholds")
        return Zepa(
            id = obj.getString("id"),
            name = obj.getString("name"),
            noiseThresholds = NoiseThresholds(
                dbSafe = noise.getInt("db_safe"),
                dbWarning = noise.getInt("db_warning")
            ),
            areaHa = obj.optDouble("area_ha").takeUnless { it.isNaN() },
            dateSpa = obj.optString("date_spa").ifEmpty { null },
            spaLegalRef = obj.optString("spa_legal_ref").ifEmpty { null },
            description = obj.optString("description").ifEmpty { null },
            quality = obj.optString("quality").ifEmpty { null },
            habitats = parseHabitats(obj.optJSONArray("habitats")),
            species = parseSpecies(obj.optJSONArray("species")),
            impacts = parseImpacts(obj.optJSONArray("impacts")),
            management = parseManagement(obj.optJSONArray("management")),
            geometry = parseGeometry(obj.getJSONObject("geometry"))
        )
    }

    fun toZepa(json: String): List<Zepa> {
        val root = JSONObject(json)
        val data = root.getJSONArray("data")
        val result = mutableListOf<Zepa>()

        for (i in 0 until data.length()) {
            result.add(parseZepa(data.getJSONObject(i)))
        }

        return result
    }
}
