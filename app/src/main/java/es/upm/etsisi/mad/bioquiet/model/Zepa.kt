package es.upm.etsisi.mad.bioquiet.model

data class NoiseThresholds(
    val dbSafe: Int,
    val dbWarning: Int
)

data class Habitat(
    val code: String?,
    val description: String?,
    val priority: Boolean?,
    val coverHa: Double?,
    val representativity: String?,
    val conservation: String?,
    val globalAssessment: String?
)

data class Species(
    val code: String?,
    val name: String?,
    val group: String?,
    val populationType: String?,
    val abundance: String?,
    val conservation: String?,
    val global: String?
)

data class Impact(
    val code: String?,
    val description: String?,
    val intensity: String?,
    val occurrence: String?,
    val type: String?
)

data class Management(
    val orgName: String?,
    val orgEmail: String?,
    val planUrl: String?,
    val measures: String?
)

data class GeoJsonGeometry(
    val type: String,
    val coordinates: Any
)

data class Zepa(
    val id: String,
    val name: String,
    val noiseThresholds: NoiseThresholds,
    val areaHa: Double?,
    val dateSpa: String?,
    val spaLegalRef: String?,
    val description: String?,
    val quality: String?,
    val habitats: List<Habitat>,
    val species: List<Species>,
    val impacts: List<Impact>,
    val management: List<Management>,
    val geometry: GeoJsonGeometry
)
