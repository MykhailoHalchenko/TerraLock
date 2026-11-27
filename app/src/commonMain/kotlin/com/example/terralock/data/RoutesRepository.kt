package com.example.terralock.data

import com.example.terralock.routing.HybridRoadRouter
import com.example.terralock.routing.RoadEdge
import com.example.terralock.routing.RoadGraph
import com.example.terralock.routing.RoadNode
import com.example.terralock.routing.VehicleProfile
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
enum class RouteMode {
    DRIVING, TRANSIT, WALKING, BICYCLING, TAXI
}

@Serializable
data class RouteRequest(
    val originLat: Double,
    val originLon: Double,
    val destLat: Double,
    val destLon: Double,
    val waypoints: List<Pair<Double, Double>> = emptyList()
)

@Serializable
data class RouteOption(
    val mode: RouteMode,
    val durationText: String,
    val distanceText: String,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val polylinePoints: List<Pair<Double, Double>>,
    val elevationProfile: List<Double> = emptyList(),
    val slopePcts: List<Double> = emptyList(),
    val totalAscentMeters: Double = 0.0,
    val totalDescentMeters: Double = 0.0,
    val isOfflineWarning: Boolean = false
)

@Serializable
data class MultiModalRoutes(
    val routes: Map<RouteMode, RouteOption>
)

class RoutesRepository(
    private val httpClient: HttpClient = HttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val userAgent = "TerraLock-Android/1.0"

    private val store: Store<RouteRequest, MultiModalRoutes> = StoreBuilder
        .from(
            fetcher = Fetcher.of { req: RouteRequest ->
                fetchOsrmRoutesOnlineOrFallback(req)
            }
        )
        .build()

    suspend fun getRoutes(request: RouteRequest): MultiModalRoutes {
        return try {
            val response = store.stream(StoreReadRequest.cached(request, refresh = false))
                .first { it is StoreReadResponse.Data }
            response.requireData()
        } catch (_: Exception) {
            computeLocalRoadGraphRoutes(request)
        }
    }

    private suspend fun fetchOsrmRoutesOnlineOrFallback(req: RouteRequest): MultiModalRoutes {
        return try {
            val modes = listOf(RouteMode.DRIVING, RouteMode.WALKING, RouteMode.BICYCLING)
            val resultMap = mutableMapOf<RouteMode, RouteOption>()

            for (mode in modes) {
                val option = fetchOsrmRouteMode(req, mode)
                val enrichedOption = enrichRouteWithElevations(option)
                resultMap[mode] = enrichedOption
            }

            resultMap[RouteMode.DRIVING]?.let { driving ->
                val taxiDuration = driving.durationSeconds + 300L
                val mins = (taxiDuration / 60).coerceAtLeast(1)
                resultMap[RouteMode.TAXI] = driving.copy(
                    mode = RouteMode.TAXI,
                    durationText = "$mins min (Taxi)",
                    durationSeconds = taxiDuration
                )

                val transitDuration = (driving.durationSeconds * 1.3).toLong() + 180L
                val transitMins = (transitDuration / 60).coerceAtLeast(1)
                resultMap[RouteMode.TRANSIT] = driving.copy(
                    mode = RouteMode.TRANSIT,
                    durationText = "$transitMins min (Bus/Metro)",
                    durationSeconds = transitDuration
                )
            }

            MultiModalRoutes(resultMap)
        } catch (_: Exception) {
            computeLocalRoadGraphRoutes(req)
        }
    }

    private suspend fun fetchOsrmRouteMode(req: RouteRequest, mode: RouteMode): RouteOption {
        val osrmProfile = when (mode) {
            RouteMode.DRIVING, RouteMode.TAXI -> "car"
            RouteMode.WALKING, RouteMode.TRANSIT -> "foot"
            RouteMode.BICYCLING -> "bike"
        }

        val coordsStr = buildString {
            append("${req.originLon},${req.originLat}")
            req.waypoints.forEach { wp ->
                append(";${wp.second},${wp.first}")
            }
            append(";${req.destLon},${req.destLat}")
        }

        val url = "https://router.project-osrm.org/route/v1/$osrmProfile/$coordsStr?overview=full&geometries=geojson&steps=true"

        val response = httpClient.get(url) {
            header("User-Agent", userAgent)
        }

        if (response.status.isSuccess()) {
            val bodyStr = response.bodyAsText()
            val parsed = parseOsrmResponse(bodyStr, mode, req)
            if (parsed != null) return parsed
        }

        return computeLocalRoadGraphForMode(req, mode)
    }

    private fun parseOsrmResponse(jsonStr: String, mode: RouteMode, req: RouteRequest): RouteOption? {
        return try {
            val root = json.parseToJsonElement(jsonStr).jsonObject
            val routesArray = root["routes"]?.jsonArray ?: return null
            if (routesArray.isEmpty()) return null

            val primaryRoute = routesArray[0].jsonObject
            val distanceMeters = primaryRoute["distance"]?.jsonPrimitive?.double ?: 0.0
            val durationSeconds = primaryRoute["duration"]?.jsonPrimitive?.double?.toLong() ?: 0L

            val geometryObj = primaryRoute["geometry"]?.jsonObject ?: return null
            val coordinatesArray = geometryObj["coordinates"]?.jsonArray ?: return null

            val points = coordinatesArray.map { coord ->
                val arr = coord.jsonArray
                Pair(arr[1].jsonPrimitive.double, arr[0].jsonPrimitive.double)
            }

            val mins = (durationSeconds / 60).coerceAtLeast(1)
            val km = distanceMeters / 1000.0

            RouteOption(
                mode = mode,
                durationText = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "$mins min",
                distanceText = if (km >= 1.0) "${"%.1f".format(km)} km" else "${distanceMeters.toInt()} m",
                durationSeconds = durationSeconds,
                distanceMeters = distanceMeters,
                polylinePoints = points
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun enrichRouteWithElevations(option: RouteOption): RouteOption {
        if (option.polylinePoints.isEmpty()) return option

        val points = option.polylinePoints
        val sampleSize = min(40, points.size)
        val step = max(1, points.size / sampleSize)

        val sampledPoints = mutableListOf<Pair<Double, Double>>()
        for (i in points.indices step step) {
            sampledPoints.add(points[i])
        }
        if (sampledPoints.lastOrNull() != points.last()) {
            sampledPoints.add(points.last())
        }

        val lats = sampledPoints.map { it.first }
        val lons = sampledPoints.map { it.second }

        val elevations = fetchOpenMeteoElevations(lats, lons)
        if (elevations.isEmpty()) return option

        var totalAscent = 0.0
        var totalDescent = 0.0
        val slopePcts = mutableListOf<Double>()
        slopePcts.add(0.0)

        for (i in 0 until sampledPoints.size - 1) {
            val dist = calculateHaversineDistance(
                sampledPoints[i].first, sampledPoints[i].second,
                sampledPoints[i + 1].first, sampledPoints[i + 1].second
            )
            val deltaElev = elevations[i + 1] - elevations[i]

            if (deltaElev > 0) totalAscent += deltaElev else totalDescent += abs(deltaElev)

            val slope = if (dist > 1.0) (deltaElev / dist) * 100.0 else 0.0
            slopePcts.add(slope)
        }

        return option.copy(
            elevationProfile = elevations,
            slopePcts = slopePcts,
            totalAscentMeters = totalAscent,
            totalDescentMeters = totalDescent
        )
    }

    private suspend fun fetchOpenMeteoElevations(lats: List<Double>, lons: List<Double>): List<Double> {
        return try {
            val latsParam = lats.joinToString(",")
            val lonsParam = lons.joinToString(",")
            val url = "https://api.open-meteo.com/v1/elevation?latitude=$latsParam&longitude=$lonsParam"
            val response = httpClient.get(url) {
                header("User-Agent", userAgent)
            }
            if (response.status.isSuccess()) {
                val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                root["elevation"]?.jsonArray?.map { it.jsonPrimitive.double } ?: emptyList()
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun computeLocalRoadGraphRoutes(req: RouteRequest): MultiModalRoutes {
        val graph = fetchOverpassRoadGraph(req)
        val router = HybridRoadRouter(graph)

        val map = mutableMapOf<RouteMode, RouteOption>()
        for (mode in RouteMode.entries) {
            val res = router.calculateRoute(req.originLat, req.originLon, req.destLat, req.destLon, mode, VehicleProfile())
            val mins = (res.totalDurationSeconds / 60).coerceAtLeast(1)
            val km = res.totalDistanceMeters / 1000.0

            map[mode] = RouteOption(
                mode = mode,
                durationText = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "$mins min",
                distanceText = if (km >= 1.0) "${"%.1f".format(km)} km" else "${res.totalDistanceMeters.toInt()} m",
                durationSeconds = res.totalDurationSeconds,
                distanceMeters = res.totalDistanceMeters,
                polylinePoints = res.points,
                elevationProfile = emptyList(),
                slopePcts = res.slopePcts,
                totalAscentMeters = res.totalAscentMeters,
                totalDescentMeters = res.totalDescentMeters,
                isOfflineWarning = graph.nodes.isEmpty()
            )
        }
        return MultiModalRoutes(map)
    }

    private suspend fun fetchOverpassRoadGraph(req: RouteRequest): RoadGraph {
        val graph = RoadGraph()
        try {
            val minLat = min(req.originLat, req.destLat) - 0.01
            val maxLat = max(req.originLat, req.destLat) + 0.01
            val minLon = min(req.originLon, req.destLon) - 0.01
            val maxLon = max(req.originLon, req.destLon) + 0.01

            val overpassQuery = """[out:json][timeout:10];way["highway"]($minLat,$minLon,$maxLat,$maxLon);(._;>;);out body;"""
            val response = httpClient.post("https://overpass-api.de/api/interpreter") {
                header("User-Agent", userAgent)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("data=$overpassQuery")
            }

            if (response.status.isSuccess()) {
                val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val elements = root["elements"]?.jsonArray ?: return graph

                for (elem in elements) {
                    val obj = elem.jsonObject
                    val type = obj["type"]?.jsonPrimitive?.content ?: continue

                    if (type == "node") {
                        val id = obj["id"]?.jsonPrimitive?.long ?: continue
                        val lat = obj["lat"]?.jsonPrimitive?.double ?: continue
                        val lon = obj["lon"]?.jsonPrimitive?.double ?: continue
                        graph.addNode(RoadNode(id, lat, lon))
                    } else if (type == "way") {
                        val wayId = obj["id"]?.jsonPrimitive?.long ?: continue
                        val nodesArray = obj["nodes"]?.jsonArray ?: continue
                        val tags = obj["tags"]?.jsonObject
                        val highwayType = tags?.get("highway")?.jsonPrimitive?.content ?: "secondary"
                        val oneWay = tags?.get("oneway")?.jsonPrimitive?.content == "yes"

                        for (i in 0 until nodesArray.size - 1) {
                            val fromId = nodesArray[i].jsonPrimitive.long
                            val toId = nodesArray[i + 1].jsonPrimitive.long
                            val fromNode = graph.nodes[fromId]
                            val toNode = graph.nodes[toId]

                            if (fromNode != null && toNode != null) {
                                val dist = calculateHaversineDistance(fromNode.lat, fromNode.lon, toNode.lat, toNode.lon)
                                graph.addEdge(
                                    RoadEdge(
                                        id = "${wayId}_$i",
                                        fromId = fromId,
                                        toId = toId,
                                        lengthM = dist,
                                        maxSpeedKmh = 50.0,
                                        highwayType = highwayType,
                                        oneWay = oneWay
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return graph
    }

    private fun computeLocalRoadGraphForMode(req: RouteRequest, mode: RouteMode): RouteOption {
        val distMeters = calculateHaversineDistance(req.originLat, req.originLon, req.destLat, req.destLon)
        val speedMps = when (mode) {
            RouteMode.DRIVING -> 13.88
            RouteMode.TRANSIT -> 8.33
            RouteMode.WALKING -> 1.38
            RouteMode.BICYCLING -> 4.16
            RouteMode.TAXI -> 13.88
        }
        val durationSec = (distMeters / speedMps).toLong()
        val mins = (durationSec / 60).coerceAtLeast(1)
        val km = distMeters / 1000.0

        val polyline = listOf(Pair(req.originLat, req.originLon), Pair(req.destLat, req.destLon))

        return RouteOption(
            mode = mode,
            durationText = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "$mins min",
            distanceText = if (km >= 1.0) "${"%.1f".format(km)} km" else "${distMeters.toInt()} m",
            durationSeconds = durationSec,
            distanceMeters = distMeters,
            polylinePoints = polyline,
            isOfflineWarning = true
        )
    }

    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}