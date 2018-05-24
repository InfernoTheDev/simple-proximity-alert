package com.voova.illnino.proximityalert

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GpsStatus.GPS_EVENT_STARTED
import android.location.GpsStatus.GPS_EVENT_STOPPED
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_main.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    companion object {
        val TAG: String = "MainActivity"
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

    lateinit var locationManager: LocationManager
    lateinit var proximities: ArrayList<ProximityPoint>
    lateinit var proxServiceIntent: Intent


    //define the listener
    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            updateDisplayView("onLocationChanged: ${location?.latitude}, ${location?.longitude}, ${location?.provider}")
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
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        getInstance = this
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        proxServiceIntent = Intent(this, ProximityBackgroundService::class.java)

        makeRequest()

        val permissionLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        if (permissionLocation == PackageManager.PERMISSION_GRANTED) {


            updateDisplayView("\nProximity start !!")

            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000,
                    0F,
                    locationListener)
            //FusedLocationManager.getInstance(this).requestLocationUpdate(locationListener)

            proximities = ArrayList()
            /*val firstPoint = ProximityPoint(995, 12.880355, 100.895128, 50F, "Voova")
            val secondPoint = ProximityPoint(996, 12.879880, 100.893853, 50F, "Ek MongKhon")
            val thirdPoint = ProximityPoint(997, 12.878389, 100.897001, 50F,  "Kanya-Phak apartment")
            val fourthPoint = ProximityPoint(998, 12.877134, 100.885354, 50F, "Beach Police Booth Chaiyaphruek Rd")
            val fifthPoint = ProximityPoint(999,  12.879258, 100.883723, 50F, "SCB Jomtien")

            proximities.add(firstPoint)
            proximities.add(secondPoint)
            proximities.add(thirdPoint)
            proximities.add(fourthPoint)
            proximities.add(fifthPoint)

            addProximity(proximities)*/

            btn_search.setOnClickListener{ loadGeofenceFromServer() }

        }else{
            updateDisplayView("\nPermission denied !!")
            Toast.makeText(this, "Permission denied !!", Toast.LENGTH_LONG).show()
        }
    }

    private fun makeRequest() {
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                0)
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
        if(proxServiceIntent != null) {
            startService(proxServiceIntent)
            isProxBackgroundServiceRunning = true
        }
    }
    private fun stopProxBackgroundService() {
        if (isProxBackgroundServiceRunning) {
            stopService(proxServiceIntent)
            isProxBackgroundServiceRunning = false
        }
    }

    fun updateDisplayView(txt: String){
        val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
        val currentDate = sdf.format(Date())
        runOnUiThread {
            tv_display_status.append("$currentDate: $txt\n")
        }
        //sv_display.fullScroll(View.FOCUS_DOWN)
    }

    fun loadGeofenceFromServer(){
        var url = edt_search_box.text.toString()

        if (url.equals("")) return

        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.BODY
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        try {
            val retrofit = Retrofit
                    .Builder()
                    .baseUrl("http://host/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()

            val geofencePointService = retrofit.create(GeofencePointService::class.java)
            val request = geofencePointService.geofenceList(url)

            MainActivity.getInstance.updateDisplayView("loadGeofenceFromServer start !!")

            executeOnBackground(request)

        } catch (ex: IllegalArgumentException){
            MainActivity.getInstance.updateDisplayView(ex.message+"")
        }
    }

    private fun executeOnBackground(request: Call<List<ProximityPoint>>) {

        Observable.just(true)
                .subscribeOn(Schedulers.io())
                .map {
                    updateDisplayView("executeOnBackground start !!")
                    val response = request.execute()
                    return@map response
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { it ->
                            updateDisplayView("loadGeofenceFromServer done !!")
                            if (it.isSuccessful) {
                                updateDisplayView("loadGeofenceFromServer success body: ${it.body()}")
                                updateDisplayView("loadGeofenceFromServer success message: ${it.message()}")

                                removeProximity()
                                proximities.clear()

                                proximities = it.body() as ArrayList<ProximityPoint>
                                /*for (p in proximities){
                                    updateDisplayView("loadGeofenceFromServer success proxs: ${p.name}")
                                }*/

                                addProximity(proximities)
                            } else {
                                updateDisplayView("loadGeofenceFromServer fail: ${it.isSuccessful} !! ${it.errorBody()}")
                            }
                        },
                        { e ->
                            updateDisplayView("loadGeofenceFromServer error: ${e.message}")
                        },
                        { updateDisplayView("loadGeofenceFromServer complete !!") })

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
        //FusedLocationManager.getInstance(this).removeUpdates(locationListener)
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
}