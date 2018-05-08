package com.voova.illnino.proximityalert

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.*
import android.location.GpsStatus.GPS_EVENT_STARTED
import android.location.GpsStatus.GPS_EVENT_STOPPED
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_main.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        lateinit var getInstance: MainActivity
        val ProximityAlert = "ProximityAlert"
    }
    val TAG: String = "MainActivity"
    lateinit var locationManager: LocationManager
    lateinit var proximities: ArrayList<ProximityPoint>

    //define the listener
    val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            updateDisplayView("onLocationChanged: ${location?.latitude}, ${location?.longitude}")
        }
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            for (i in extras.keySet()){
                Log.d(TAG, "onStatusChanged extra: ${extras[i]}")
            }
            var statustxt = ""
            if (status == GPS_EVENT_STARTED){
                statustxt = "GPS_EVENT_STARTED"
            } else if (status == GPS_EVENT_STOPPED) {
                statustxt = "GPS_EVENT_STOPPED"
            } else {
                statustxt = status.toString()
            }
            updateDisplayView("onStatusChanged: ${provider}, status: ${statustxt}")
        }
        override fun onProviderEnabled(provider: String) {

        }
        override fun onProviderDisabled(provider: String) {

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        getInstance = this

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        if (permission == PackageManager.PERMISSION_GRANTED) {

            updateDisplayView("\nProximity start !!")
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000,
                    0F,
                    locationListener)

            proximities = ArrayList()
            val firstPoint = ProximityPoint(996, 12.880350, 100.895056, 15F, "Voova")
            val secondPoint = ProximityPoint(997, 12.880096, 100.894160, 15F, "Sea max condo")
            val thirdPoint = ProximityPoint(998, 12.880608, 100.895997, 15F, "Aunty's restaurant")
            val fourthPoint = ProximityPoint(999, 12.877134, 100.885354, 15F, "Beach Police Booth Chaiyaphruek Rd")

            proximities.add(firstPoint)
            proximities.add(secondPoint)
            proximities.add(thirdPoint)
            proximities.add(fourthPoint)

            addProximity(proximities)

        }else{
            updateDisplayView("\nPermission denied !!")
            Toast.makeText(this, "Permission denied !!", Toast.LENGTH_LONG).show()
        }
    }
    fun updateDisplayView(txt: String){
        val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
        val currentDate = sdf.format(Date())
        tv_display_status.append("$currentDate: $txt\n")
        //sv_display.fullScroll(View.FOCUS_DOWN)
    }

    @SuppressLint("MissingPermission")
    fun addProximity(proxList: ArrayList<ProximityPoint>){
        for (prox in proxList){
            updateDisplayView("Add proximity point : ${prox.name}, ${prox.lat}, ${prox.lon}, radius: ${prox.radius}")
            val intent:Intent = Intent(ProximityAlert).apply {
                putExtra("name", prox.name)
            }

            val pendingIntent: PendingIntent = PendingIntent.getBroadcast(this, prox.id, intent, PendingIntent.FLAG_UPDATE_CURRENT)

            locationManager.addProximityAlert(
                    prox.lat,
                    prox.lon,
                    prox.radius,
                    -1,
                    pendingIntent
                    )
        }
    }

    private fun removeProximity(){

        for (prox in proximities){
            Log.d(TAG, "removeProximity id: ${prox.id}")
            val intent = Intent(ProximityAlert)
            val pendingIntent = PendingIntent.getBroadcast(this , prox.id, intent, 0)
            locationManager?.removeProximityAlert(pendingIntent)
        }

    }

    fun removeRequestLocationUpdate(){
        Log.d(TAG, "removeRequestLocationUpdate")
        locationManager.removeUpdates(locationListener)
    }

    data class ProximityPoint(
            var id: Int,
            var lat: Double,
            var lon: Double,
            var radius: Float,
            var name: String
    )

    override fun onDestroy() {
        removeProximity()
        removeRequestLocationUpdate()
        super.onDestroy()

    }

    class GeofenceTransitionsIntentService : BroadcastReceiver() {
        val TAG: String = "GeofenceReceiver"
        override fun onReceive(context: Context?, intent: Intent) {
            Log.d(TAG, "Fire !!")
            MainActivity.getInstance.updateDisplayView("$TAG: Fire")
            for (key: String in  intent.extras.keySet()) {
                Log.d(TAG, intent.extras[key].toString())
                MainActivity.getInstance.updateDisplayView("Intent $key: ${intent.extras[key]}")
            }

            if (intent.action.equals(ProximityAlert)){

                val key: String = LocationManager.KEY_PROXIMITY_ENTERING
                val entering: Boolean = intent.getBooleanExtra(key, false)

                if (entering){
                    val m = "\n======\nYou are Entering ${intent.extras["name"]} !!\n======"
                    Log.d(TAG, m)
                    MainActivity.getInstance.updateDisplayView(m)
                    Toast.makeText(context, m, Toast.LENGTH_LONG).show()
                }else{
                    val m = "\n======\nYou are Exiting ${intent.extras["name"]} !!\n======"
                    Log.d(TAG, m)
                    MainActivity.getInstance.updateDisplayView(m)
                    Toast.makeText(context, m, Toast.LENGTH_LONG).show()
                }
            }
        }
    }


}
