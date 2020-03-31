package com.misoca.mock_location

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.LocationProvider
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*

class MockLocationService : Service() {

    companion object {
        private const val CHANNEL_ID = "MOCK_LOCATION"
        private const val CHANNEL_NAME = "位置情報偽装"

        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_TITLE = "MockLocationService"

        private const val INTENT_ACTION_START = "INTENT_ACTION_START"
        private const val INTENT_ACTION_STOP = "INTENT_ACTION_STOP"
        private const val INTENT_EXTRA_LOCATION = "INTENT_EXTRA_LATITUDE"
        private const val INTENT_EXTRA_PACKAGE_NAME = "INTENT_EXTRA_PACKAGE_NAME"
        private const val INTERVAL = 3_000L

        fun start(context: Context, latitude: Double, longitude: Double) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = INTENT_ACTION_START
                putExtra(INTENT_EXTRA_LOCATION, Location(LocationManager.GPS_PROVIDER).apply {
                    this.latitude = latitude
                    this.longitude = longitude
                })
                putExtra(INTENT_EXTRA_PACKAGE_NAME, context.packageName)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = INTENT_ACTION_STOP
            }
            context.startService(intent)
        }

    }

    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    val locationManager: LocationManager by lazy {
        getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(applicationContext)
    }

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + supervisorJob)
    private var job: Job? = null

    private var latitude: Double? = null
    private var longitude: Double? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannelIfNeeded()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            INTENT_ACTION_START -> {
                val location = intent.getParcelableExtra<Location>(INTENT_EXTRA_LOCATION)
                val packageName = intent.getStringExtra(INTENT_EXTRA_PACKAGE_NAME)
                startMockLocation(location, packageName)
            }
            INTENT_ACTION_STOP -> {
                stopMockLocation()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startMockLocation(location: Location, packageName: String) {
if (!isMockAppEnabled(packageName)) {
    // 仮の位置情報アプリに設定されていない
    showToast("位置情報を設定するには仮の位置情報アプリに設定してください")
    startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
    return
}

        clearMockLocation()

        if (setMockLocation(location.latitude, location.longitude)) {
            this.latitude = location.latitude
            this.longitude = location.longitude
            showToast("位置情報の偽装を開始しました")
            updateNotification("now mock location is $latitude, $longitude")
            job?.cancel()
            job = scope.launch {
                while (true) {
                    delay(INTERVAL)
                    if (!setMockLocation(location.latitude, location.longitude)) stopMockLocation()
                }
            }
        } else {
            showToast("位置情報の偽装に失敗しました")
        }
    }

    private fun stopMockLocation() {
        job?.cancel()
        clearMockLocation()
        removeNotification()
        showToast("位置情報の偽装を終了しました")
        stopSelf()
    }

    private fun isMockAppEnabled(packageName: String): Boolean {
        try {
            val opsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            return opsManager.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
        } catch (e: SecurityException) {
            // MOCK_LOCATIONにアプリが設定されていないとcheckOpでSecurityExceptionが発生する
            return false
        }
    }

    private fun setMockLocation(latitude: Double, longitude: Double): Boolean {
        try {
            for (provider in providers) {
                locationManager.run {
                    addTestProvider(
                        provider,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_FINE
                    )
                    setTestProviderEnabled(provider, true)
                    setTestProviderLocation(provider, Location(provider).apply {
                        this.latitude = latitude
                        this.longitude = longitude
                        this.altitude = 0.0
                        this.accuracy = 500f
                        this.time = System.currentTimeMillis()
                        this.speed = 0f
                        this.elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                    })
                }
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun clearMockLocation() {
        try {
            for (provider in providers) {
                locationManager.run {
                    removeTestProvider(provider)
                }
            }
        } catch (e: IllegalArgumentException) {
            // addTestProviderされていない状態でremoveTestProviderを呼ぶとIllegalArgumentExceptionが発生
        }
    }

    private fun showToast(message: String) {
        scope.launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()

            }
        }
    }

    private fun registerNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val contentText = "Running now."
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID).run {
            setSmallIcon(R.mipmap.ic_launcher)
            setContentTitle(NOTIFICATION_TITLE)
            setContentText(contentText)
            build()
        }
    }

    private fun updateNotification(text: String) {
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            Intent(applicationContext, MockLocationService::class.java).apply {
                action = INTENT_ACTION_STOP
            },
            0
        )
        val action = NotificationCompat.Action(R.drawable.ic_close, "位置偽装を終了する", stopPendingIntent)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID).run {
            setSmallIcon(R.mipmap.ic_launcher)
            setContentTitle(NOTIFICATION_TITLE)
            setContentText(text)
            addAction(action)
            build()
        }
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun removeNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

}
