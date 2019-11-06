package be.ap.edu.velostationmap

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.json.JSONArray

import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.ItemizedOverlay
import org.osmdroid.views.overlay.MinimapOverlay
import org.osmdroid.views.overlay.OverlayItem
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.ArrayList

class MainActivity : Activity() {

    val parser: Parser = Parser.default()
    var veloJson: JsonArray<JsonObject>? = null

    var mMapView: MapView? = null
    var mMyLocationOverlay: ItemizedOverlay<OverlayItem>? = null
    var searchField: EditText? = null
    var searchButton: Button? = null
    var clearButton: Button? = null
    val urlSearch = "https://nominatim.openstreetmap.org/search?q="



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val json = application.assets.open("velostation.json")
        veloJson = parser.parse(json) as JsonArray<JsonObject>

        // Problem with SQLite db, solution :
        // https://stackoverflow.com/questions/40100080/osmdroid-maps-not-loading-on-my-device
        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = packageName
        val basePath = File(cacheDir.absolutePath, "osmdroid")
        osmConfig.osmdroidBasePath = basePath
        val tileCache = File(osmConfig.osmdroidBasePath, "tile")
        osmConfig.osmdroidTileCache = tileCache

        setContentView(R.layout.activity_main)

        mMapView = findViewById(R.id.mapview) as MapView

        searchField = findViewById(R.id.search_txtview)
        searchButton = findViewById(R.id.search_button)
        searchButton!!.setOnClickListener {
            val url = URL(urlSearch + URLEncoder.encode(searchField?.text.toString(), "UTF-8") + "&format=json")
            it.hideKeyboard()

            MyAsyncTask().execute(url)
        }

        clearButton = findViewById(R.id.clear_button)
        clearButton!!.setOnClickListener {
            mMapView!!.overlays.clear()
            // Redraw map
            mMapView!!.invalidate()
        }

        if (hasPermissions()) {
            initMap()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }

    }

    fun View.hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (hasPermissions()) {
                initMap()
            } else {
                finish()
            }
        }
    }

    fun initMap() {
        mMapView!!.setTileSource(TileSourceFactory.MAPNIK)



        run {
            // Create a static ItemizedOverlay showing a some Markers on some cities
            val items = ArrayList<OverlayItem>()

            var i: Int? = null

            for (station: JsonObject in veloJson!!){
                var naam: String = station.string("naam").toString()
                var objType: String = station.string("obj_type").toString()
                var lat: Double = station.double("point_lat").toString().toDouble()
                var lng: Double = station.double("point_lng").toString().toDouble()

                items.add(
                    OverlayItem(naam, objType, GeoPoint(lat, lng))
                )
            }

            // OnTapListener for the Markers, shows a simple Toast
            this.mMyLocationOverlay = ItemizedIconOverlay(items,
                object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                    override fun onItemSingleTapUp(index: Int, item: OverlayItem): Boolean {
                        clickedOnMarker(item.title)
                        return true // We 'handled' this event.
                    }

                    override fun onItemLongPress(index: Int, item: OverlayItem): Boolean {
                        clickedOnMarker(item.title)
                        return true
                    }

                }, applicationContext)
            this.mMapView!!.overlays.add(this.mMyLocationOverlay)
        }


        val mapController = mMapView!!.controller
        mapController.setZoom(20.0)
        // Default = Ellermanstraat 33
        mapController.setCenter(GeoPoint(51.23020595, 4.41655480828479))
    }

    fun clickedOnMarker(naam: String){
        intent =  Intent(this, VeloActivity::class.java)
        intent.putExtra("naam", naam)
        startActivity(intent)
    }

    fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onPause() {
        super.onPause()
        mMapView!!.onPause()
    }

    override fun onResume() {
        super.onResume()
        mMapView!!.onResume()
    }

    fun streamToString(inputStream: InputStream): String {

        val bufferReader = BufferedReader(InputStreamReader(inputStream))
        var line: String
        var result = ""

        try {
            do {
                line = bufferReader.readLine()
                if (line != null) {
                    result += line
                }
            } while (line != "")
            inputStream.close()
        } catch (ex: Exception) {

        }
        return result
    }

    // AsyncTask inner class
    inner class MyAsyncTask : AsyncTask<URL, Int, String>() {

        private var result: String = ""
        private val parser: Parser = Parser.default()

        override fun onPreExecute() {
            super.onPreExecute()
        }

        override fun doInBackground(vararg params: URL?): String {

            val connect = params[0]?.openConnection() as HttpURLConnection
            connect.readTimeout = 8000
            connect.connectTimeout = 8000
            connect.requestMethod = "GET"
            connect.connect();

            val responseCode: Int = connect.responseCode;
            if (responseCode == 200) {
                result = streamToString(connect.inputStream)
            }

            return result
        }

        // vararg : variable number of arguments
        // * : spread operator, unpacks an array into the list of values from the array
        override fun onProgressUpdate(vararg values: Int?) {
            super.onProgressUpdate(*values)
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)

            // Parse result as Array of JSON objects
            val jsonString = StringBuilder(result!!)
            val parser: Parser = Parser.default()
            val array = parser.parse(jsonString) as JsonArray<JsonObject>

            if (array.size > 0) {
                // Use low-level API
                val obj = array[0]
                val mapController = mMapView!!.controller
                mapController.setCenter(GeoPoint(obj.string("lat")!!.toDouble(), obj.string("lon")!!.toDouble()))
            }
        }
    }

}
