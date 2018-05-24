
/*
 * Copyright (c) 2015 PathSense, Inc.
 */
package com.voova.illnino.proximityalert
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log

import java.util.ArrayList
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

class FusedLocationManager {
    companion object {
        internal val TAG = FusedLocationManager::class.java.name
        @Volatile private var sInstance: FusedLocationManager? = null
        private lateinit var mContext: Context
        fun  getInstance(context: Context): FusedLocationManager {
            synchronized(this){
                if (sInstance == null) {
                    sInstance = FusedLocationManager()
                    mContext = context
                }
                return sInstance as FusedLocationManager
            }
        }

    }

    // ---------------------- Instance Fields
    internal var mLocationManager: LocationManager? = null
    internal var mHolders: Queue<InternalHolder>? = ConcurrentLinkedQueue()

    internal class InternalHolder {
        var mListener: LocationListener? = null
        var mFilterList: MutableList<InternalLocationListenerFilter>? = null
    }

    internal class InternalLocationListenerFilter(var mManager: FusedLocationManager?, var mListener: LocationListener?) : LocationListener {
        override fun onLocationChanged(location: Location) {
            val manager = mManager
            val listener = mListener

            Log.d(TAG, "FusedLocationManager onLocationChanged: ${location?.latitude}, ${location?.longitude}, ${location?.provider}")
            Log.d(TAG, "accuracy: ${location?.accuracy} meters ")

            MainActivity.getInstance.updateDisplayView("FusedLocationManager onLocationChanged: ${location?.latitude}, ${location?.longitude}, ${location?.provider}")
            MainActivity.getInstance.updateDisplayView("accuracy: ${location?.accuracy} meters ")

            if (manager != null && listener != null) {
                if (manager.validate(location) && manager.removeUpdates(listener)) {
                //if (manager.validate(location)) {
                    Log.d(TAG, "FusedLocationManager broadcast location: ")
                    MainActivity.getInstance.updateDisplayView("FusedLocationManager broadcast location !!")
                    // broadcast location
                    listener.onLocationChanged(location)
                }
            }
        }

        override fun onProviderDisabled(s: String) {}
        override fun onProviderEnabled(s: String) {}
        override fun onStatusChanged(s: String, i: Int, bundle: Bundle) {}
    }

    internal fun removeUpdates(listener: LocationListener): Boolean {
        val holders = mHolders
        val locationManager = mLocationManager
        if (holders != null && locationManager != null) {
            synchronized(holders) {
                val q = holders.iterator()
                while (q.hasNext()) {
                    val holder = q.next()
                    if (holder.mListener === listener) {
                        val filters = holder.mFilterList
                        val numFilters = filters?.size ?: 0
                        for (i in numFilters - 1 downTo -1 + 1) {
                            val filter = filters!!.removeAt(i)
                            locationManager.removeUpdates(filter)
                        }
                        q.remove()
                        return true
                    }
                }
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    fun requestLocationUpdate(listener: LocationListener) {

        mLocationManager = mContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val locationManager = mLocationManager
        val holders = mHolders
        //
        if (locationManager != null && holders != null) {
            val providers = locationManager.getProviders(true)
            val numProviders = providers?.size ?: 0
            if (numProviders > 0) {
                // broadcast last known location if valid
                for (i in 0 until numProviders) {
                    val provider = providers[i]
                    val lastKnownLocation = locationManager.getLastKnownLocation(provider)
                    if (validate(lastKnownLocation)) {
                        listener.onLocationChanged(lastKnownLocation)
                        return
                    }
                }
                // request location updates
                val holder = InternalHolder()
                holder.mListener = listener
                holder.mFilterList = ArrayList(numProviders)
                //
                for (i in 0 until numProviders) {
                    val provider = providers[i]
                    val filter = InternalLocationListenerFilter(this, listener)
                    locationManager.requestLocationUpdates(provider, 3000, 0f, filter)
                    holder.mFilterList!!.add(filter)
                }
                holders.add(holder)
            }
        }
    }

    internal fun validate(location: Location?): Boolean {
        if (location != null) {
            val provider = location.provider
            if (LocationManager.NETWORK_PROVIDER == provider) {
                val accuracy = location.accuracy.toDouble()
                val age = System.currentTimeMillis() - location.time
                Log.d(TAG, "provider=$provider,accuracy=$accuracy,age=$age")
                if (location.accuracy <= 50.0 && age <= 20000) {
                    return true
                }
            } else if (LocationManager.GPS_PROVIDER == provider) {
                Log.d(TAG, "provider=$provider,accuracy=${location.accuracy}")
                if (location.accuracy <= 50.0 && System.currentTimeMillis() - location.time <= 5000) {
                    return true
                }
            }
        }
        return false
    }
}