package com.voova.illnino.proximityalert

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Url

interface GeofencePointService {
    @GET
    fun geofenceList(@Url url: String): Call<List<ProximityPoint>>
}