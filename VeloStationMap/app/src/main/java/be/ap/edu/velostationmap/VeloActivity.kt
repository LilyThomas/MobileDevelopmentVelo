package be.ap.edu.velostationmap

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser

import kotlinx.android.synthetic.main.activity_velo.*

class VeloActivity : AppCompatActivity() {

    val parser: Parser = Parser.default()
    var veloJson: JsonArray<JsonObject>? = null

    var naamText: TextView? = null
    var locText: TextView? = null
    var backButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_velo)

        val json = application.assets.open("velostation.json")
        veloJson = parser.parse(json) as JsonArray<JsonObject>

        naamText = findViewById(R.id.title_name_velo)
        locText = findViewById(R.id.title_loc_velo)
        backButton = findViewById(R.id.btn_back)

        val extras: Bundle? = intent.extras

        val naam = extras?.get("naam")

        naamText?.text = naam.toString()

        for(station in veloJson!!){
            if(station.string("naam").equals(naam.toString())){
                locText?.text = station.int("aantal_loc").toString()
            }
        }

        backButton!!.setOnClickListener(){
            intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

    }

}
