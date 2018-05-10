package com.voova.illnino.proximityalert

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.*
import android.location.GpsStatus.GPS_EVENT_STARTED
import android.location.GpsStatus.GPS_EVENT_STOPPED
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_main.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.log

class MainActivity : AppCompatActivity() {
    companion object {
        lateinit var getInstance: MainActivity
        const val ProximityAlert = "ProximityAlert"

        var proximityBundle = Bundle()
        lateinit var proximityPoint: ProximityPoint
        var key: String = ""
        var proximityEnteringExitingStatus: Boolean = false

        var latestPointName: String = ""
        var latestEnteringExitingStatus:Boolean = false
        var actualEntering:Boolean = false
        var actualExit:Boolean = false
        var actualStatusCheck:Int = 0
        var isCalculating:Boolean = false
        var isProxBackgroundServiceRunning = false
    }
    val TAG: String = "MainActivity"
    lateinit var locationManager: LocationManager
    lateinit var proximities: ArrayList<ProximityPoint>
    lateinit var proxServiceIntent: Intent


    //define the listener
    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            updateDisplayView("onLocationChanged: ${location?.latitude}, ${location?.longitude}")
            updateDisplayView("accuracy: ${location?.accuracy} meters ")
        }
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            for (i in extras.keySet()){
                Log.d(TAG, "onStatusChanged extra: ${extras[i]}")
            }

            var statusTxt = when(status) {
                GPS_EVENT_STARTED -> "GPS_EVENT_STARTED"
                GPS_EVENT_STOPPED -> "GPS_EVENT_STOPPED"
                else -> status.toString()
            }
            updateDisplayView("onStatusChanged: $provider, status: $statusTxt")

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
            val firstPoint = ProximityPoint(996, 12.880355, 100.895128, 25F, "Voova")
            val secondPoint = ProximityPoint(997, 12.879973, 100.894314, 25F, "Sea max condo")
            val thirdPoint = ProximityPoint(998, 12.880558, 100.896136, 25F, "Aunty's restaurant")
            val fourthPoint = ProximityPoint(999, 12.877134, 100.885354, 25F, "Beach Police Booth Chaiyaphruek Rd")

            proximities.add(firstPoint)
            proximities.add(secondPoint)
            proximities.add(thirdPoint)
            proximities.add(fourthPoint)

            addProximity(proximities)

            proxServiceIntent = Intent(this, ProximityBackgroundService::class.java)

        }else{
            updateDisplayView("\nPermission denied !!")
            Toast.makeText(this, "Permission denied !!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        startProxBackgroundService()
    }

    override fun onResume() {
        super.onResume()
        stopProxBackgroundService()
    }

    private fun startProxBackgroundService(){
        startService(proxServiceIntent)
        isProxBackgroundServiceRunning = true
    }
    private fun stopProxBackgroundService(){
        stopService(proxServiceIntent)
        isProxBackgroundServiceRunning = false
    }

    fun updateDisplayView(txt: String){
        val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
        val currentDate = sdf.format(Date())
        runOnUiThread {
            tv_display_status.append("$currentDate: $txt\n")
        }
        //sv_display.fullScroll(View.FOCUS_DOWN)
    }

    @SuppressLint("MissingPermission")
    private fun addProximity(proxList: ArrayList<ProximityPoint>){
        for (prox in proxList){
            updateDisplayView("Add proximity point : ${prox.name}, ${prox.lat}, ${prox.lon}, radius: ${prox.radius}")
            val intent = Intent(ProximityAlert)
            var bundle = Bundle()
            bundle.putParcelable("proximity_point", prox)
            intent.putExtra("proximity_data", bundle)

            val pendingIntent: PendingIntent =
                    PendingIntent.getBroadcast(this, prox.id, intent, PendingIntent.FLAG_UPDATE_CURRENT)

            locationManager.addProximityAlert(
                    prox.lat,
                    prox.lon,
                    prox.radius,
                    -1,
                    pendingIntent)
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

    private fun removeRequestLocationUpdate(){
        Log.d(TAG, "removeRequestLocationUpdate")
        locationManager.removeUpdates(locationListener)
    }

    override fun onDestroy() {
        removeProximity()
        removeRequestLocationUpdate()
        ProximityBackgroundService.getInstance.stopSelf()
        super.onDestroy()

    }

    class GeofenceTransitionsIntentService : BroadcastReceiver() {
        val TAG: String = "GeofenceReceiver"
        override fun onReceive(context: Context?, intent: Intent) {
            Log.d(TAG, "Fire !!")
            MainActivity.getInstance.updateDisplayView("$TAG: Fire !!")
            for (key: String in  intent.extras.keySet()) {
                Log.d(TAG, intent.extras[key].toString())
                MainActivity.getInstance.updateDisplayView("Intent $key: ${intent.extras[key]}")
            }

            proximityBundle = intent.extras["proximity_data"] as Bundle
            proximityPoint = proximityBundle.getParcelable<ProximityPoint>("proximity_point")

            MainActivity.getInstance.updateDisplayView("Incoming point: ${proximityPoint.name}\n======================================")

            if (intent.action.equals(ProximityAlert)){

                key = LocationManager.KEY_PROXIMITY_ENTERING
                proximityEnteringExitingStatus = intent.getBooleanExtra(key, false)

                if (!isCalculating) {

                    isCalculating = true

                    if (!latestPointName.equals(proximityPoint.name, true)){
                        latestPointName = proximityPoint.name
                    }
                    if (latestEnteringExitingStatus != proximityEnteringExitingStatus){
                        latestEnteringExitingStatus = proximityEnteringExitingStatus
                    }

                    Observable.just(true)
                            .subscribeOn(Schedulers.io())
                            .map {

                                if (latestPointName.equals(proximityPoint.name, true) &&
                                        latestEnteringExitingStatus == proximityEnteringExitingStatus) {
                                    actualStatusCheck++
                                }

                                MainActivity.getInstance.updateDisplayView("Step 1 \nLatest pointName: $latestPointName \nNew point: ${proximityPoint.name}, \nActualStatusCheck: $actualStatusCheck")
                            }
                            .delay(2, TimeUnit.SECONDS)
                            .map {
                                if (latestPointName.equals(proximityPoint.name, true) &&
                                        latestEnteringExitingStatus == proximityEnteringExitingStatus) {
                                    actualStatusCheck++
                                }

                                MainActivity.getInstance.updateDisplayView("Step 2 \nLatest pointName: $latestPointName \nNew point: ${proximityPoint.name}, \nActualStatusCheck: $actualStatusCheck")
                            }
                            .delay(2, TimeUnit.SECONDS)
                            .map {
                                if (latestPointName.equals(proximityPoint.name, true) &&
                                        latestEnteringExitingStatus == proximityEnteringExitingStatus) {
                                    actualStatusCheck++
                                }

                                if (actualStatusCheck >= 3) {
                                    if (latestEnteringExitingStatus) {
                                        actualEntering = true
                                    } else {
                                        actualExit = true
                                    }
                                }

                                MainActivity.getInstance.updateDisplayView("Step 3 \nLatest pointName: $latestPointName \nNew point: ${proximityPoint.name}, \nActualStatusCheck: $actualStatusCheck\nlastesStatus: $latestEnteringExitingStatus\nactualEntering: $actualEntering\nactualExit: $actualExit")

                            }
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe{
                                MainActivity.getInstance.updateDisplayView("Step 4 \nactualEntering: $actualEntering \nactualExit: $actualExit")
                                var m = ""
                                if (actualEntering) {
                                    m = "\n======================================\nYou are Entering $latestPointName !!\n======================================"
                                    Log.d(TAG, m)
                                    MainActivity.getInstance.updateDisplayView(m)
                                    Toast.makeText(context, m, Toast.LENGTH_LONG).show()
                                }
                                if (actualExit) {
                                    m = "\n======================================\nYou are Exiting $latestPointName !!\n======================================"
                                    Log.d(TAG, m)
                                    MainActivity.getInstance.updateDisplayView(m)
                                    Toast.makeText(context, m, Toast.LENGTH_LONG).show()
                                }
                                Log.d(TAG, "isProxBackgroundServiceRunning: $isProxBackgroundServiceRunning")
                                if (isProxBackgroundServiceRunning) {
                                    var txt = when(latestEnteringExitingStatus){
                                        true -> "You are Entering $latestPointName"
                                        false -> "You are Exiting $latestPointName"
                                    }
                                    if(actualEntering)
                                    MainActivity.getInstance.createNotification(txt, m)
                                }

                                latestPointName = ""
                                latestEnteringExitingStatus = false
                                actualEntering = false
                                actualExit = false
                                actualStatusCheck = 0
                                isCalculating = false
                            }
                }
            }
        }
    }

    fun createNotification(content: String, m: String){
        Log.d(TAG, "createNotification !! $content")
        val notification = NotificationCompat.Builder(MainActivity.getInstance, "proximity_notification_entering_channel").apply {
            this.setSmallIcon(R.drawable.notification_icon_background)
            this.setContentTitle("Proximity Alert")
            this.setContentText(content)
            this.setStyle(NotificationCompat
                    .BigTextStyle()
                    .bigText(m))

            this.priority = NotificationCompat.PRIORITY_HIGH
        }.build()

        val notificationManager = NotificationManagerCompat.from(MainActivity.getInstance)
        notificationManager.notify(9, notification)
    }

    data class ProximityPoint(
            var id: Int,
            var lat: Double,
            var lon: Double,
            var radius: Float,
            var name: String
    ) : Parcelable {
        constructor(source: Parcel) : this(
                source.readInt(),
                source.readDouble(),
                source.readDouble(),
                source.readFloat(),
                source.readString()
        )

        override fun describeContents() = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            with(dest) {
                writeInt(id)
                writeDouble(lat)
                writeDouble(lon)
                writeFloat(radius)
                writeString(name)
            }
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<ProximityPoint> = object : Parcelable.Creator<ProximityPoint> {
                override fun createFromParcel(source: Parcel): ProximityPoint = ProximityPoint(source)
                override fun newArray(size: Int): Array<ProximityPoint?> = arrayOfNulls(size)
            }
        }
    }
}
