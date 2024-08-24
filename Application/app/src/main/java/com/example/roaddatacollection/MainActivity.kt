package com.example.road_data_collection

import android.Manifest
import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.pow

import android.content.Context
import android.content.ContentValues
import android.provider.MediaStore
import java.io.OutputStreamWriter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
// import android.compose.iu.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


// Disused imports
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Function to store data into text file
fun saveData(context: Context, fileName: String, text: String) {
    val contentResolver = context.contentResolver

    // Query for the existing file
    val projection = arrayOf(MediaStore.Files.FileColumns._ID)
    val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
    val selectionArgs = arrayOf(fileName)

    val cursor = contentResolver.query(
        MediaStore.Files.getContentUri("external"),
        projection,
        selection,
        selectionArgs,
        null
    )

    if (cursor?.moveToFirst() == true) {
        // File exists, get the URI
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
        val uri = MediaStore.Files.getContentUri("external").buildUpon().appendPath(id.toString()).build()

        // Open the file for appending
        contentResolver.openOutputStream(uri, "wa")?.use { outputStream ->
            OutputStreamWriter(outputStream).apply {
                append(text)
                flush()
            }
        } ?: run {
            println("Failed to open the file for appending.")
        }
    } else {
        // File does not exist, create a new one
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
            put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents/RoadData/")
        }

        val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(text.toByteArray())
            }
        } ?: run {
            println("Failed to create the file.")
        }
    }

    cursor?.close()
}

class MainActivity : ComponentActivity(), SensorEventListener {

    // Location data variable
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Accelerometer data variables
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var accelerometerData by mutableStateOf("") // Declare variable for discrete acceleration values
    private var accelerometerData_magnitude by mutableStateOf("") // Declare variable for magnitude of acceleration
    private var max_acceleration = 0.0 // Declare variable for maximum acceleration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestPermissions()

        // Initialize SensorManager and Accelerometer
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Listener for the Accelerometer
        accelerometer?.also { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        // Set the content view to display the composable UI
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CombinedDisplay(
                        location = locationState.value,
                        time = instanceFormatted.value,
                        discrete_acceleration_values = accelerometerData,
                        net_acceleration_magnitude = accelerometerData_magnitude,
                        max_acceleration = max_acceleration.toString(),
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPermissions() { // Request location permissions from user
        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                getLocationUpdates() // Begin getting location updates
            }
        }

        locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @SuppressLint("MissingPermission")
    private fun getLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 1000 // Update every 1 second
            fastestInterval = 1000 // Set the fastest interval to 1 second as well
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation // Get last known location
                val instance = LocalDateTime.now() // Get time at instance of runtime
                location?.let {
                    locationState.value = "${it.latitude}, ${it.longitude}" // Update location state
                }
                instance.let {
                    instanceFormatted.value = instance.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) // Update time state
                }
            }
        }, mainLooper)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) { // Pull discrete acceleration values from sensors
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Update the accelerometerData with the latest values
            accelerometerData = "X: $x,\nY: $y,\nZ: $z"
            // Get magnitude of three discrete orthogonal accelerations
            val accNet = x.toDouble().pow(2.0) + y.toDouble().pow(2.0) + z.toDouble().pow(2.0)
            val accNetMag = accNet.pow(0.5)
            // Update the accelerometerData_magnitude with the latest values
            if (accNetMag > max_acceleration) {
                max_acceleration = accNetMag
            }

            accelerometerData_magnitude = "A: $accNetMag"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Handle sensor accuracy changes if necessary
    }

    override fun onPause() {
        super.onPause()
        // Unregister the sensor when the activity is paused
        sensorManager.unregisterListener(this)
    }

    companion object {
        var locationState = mutableStateOf("GPS_buffer") // Declare companion object: locationState
        var instanceFormatted = mutableStateOf("time_buffer") // Declare companion object: instanceFormatted
    }
}

@Composable
fun CombinedDisplay(
    location: String,
    time: String,
    discrete_acceleration_values: String,
    net_acceleration_magnitude: String,
    max_acceleration: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var instant = System.currentTimeMillis() // Milliseconds since Unix epoch

    // Output:
    Text(
        text = "Your location: $location\nLast update: $time\n\nInstantaneous accelerometer data (m/s^2):\n$discrete_acceleration_values\nMagnitude of acceleration (m/s^2):\n$net_acceleration_magnitude\nMaximmum accleration (m/s^2): $max_acceleration",
        modifier = modifier.background(Color.Black),
        color = Color.Green,

    )
    // Store data into text file:

    saveData(context, "road_data.txt" , "L:$location;\nT:$instant;\nDA:\n$discrete_acceleration_values;\n====\n")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        CombinedDisplay(
            location = "GPS unavailable...",
            time = "Time unavailable...",
            discrete_acceleration_values = "X: 0.0, Y: 0.0, Z: 0.0",
            net_acceleration_magnitude = "A: 0.0",
            max_acceleration = "0.0",
        )
    }
}
