package com.nodare.geosec.data.remote.api.model

import com.google.gson.annotations.SerializedName

data class DirectionsResponse(
    val routes: List<DirectionRoute> = emptyList(),
    val status: String = ""
)

data class DirectionRoute(
    @SerializedName("overview_polyline")
    val overviewPolyline: OverviewPolyline? = null,
    val legs: List<RouteLeg> = emptyList()
)

data class OverviewPolyline(
    val points: String = ""
)

data class RouteLeg(
    val steps: List<RouteStep> = emptyList(),
    val distance: RouteValue? = null,
    val duration: RouteValue? = null
)

data class RouteStep(
    @SerializedName("start_location")
    val startLocation: LatLngLiteral? = null,
    @SerializedName("end_location")
    val endLocation: LatLngLiteral? = null,
    val polyline: OverviewPolyline? = null
)

data class LatLngLiteral(
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

data class RouteValue(
    val value: Int = 0,
    val text: String = ""
)
