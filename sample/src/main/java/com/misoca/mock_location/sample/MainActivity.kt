package com.misoca.mock_location.sample

import android.Manifest
import android.content.Context
import android.location.Criteria
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.misoca.mock_location.MockLocationService
import kotlinx.android.synthetic.main.activity_main.*
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions

@RuntimePermissions
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        button_start.setOnClickListener {
            MockLocationService.start(applicationContext, 40.7127, -74.0059)
        }
        button_check.setOnClickListener {
            checkMockWithPermissionCheck()
        }
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun checkMock() {
        Toast.makeText(applicationContext, if (isLocationMocked()) "is Mocked" else "is not Mocked", Toast.LENGTH_SHORT).show()
    }

    private fun isLocationMocked(): Boolean {
        val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val criteria = Criteria().apply { accuracy = Criteria.ACCURACY_FINE }
        val provider = locationManager.getBestProvider(criteria, true)
        val location = locationManager.getLastKnownLocation(provider)
        return location?.isFromMockProvider ?: false
    }


}
