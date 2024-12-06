package com.inblocks.meterscan

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson

class HistoryView : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_history_view)
        var toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            title = "Your History"


            // show back button on toolbar
            // on back button press, it will navigate to parent activity
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            val nav = toolbar.navigationIcon
            nav?.setTint(resources.getColor(android.R.color.black))
        }
        var recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        var list =  getDataList("meterData")
        list!!.data.reverse()
        var adapter : HistoryViewAdapter = HistoryViewAdapter(list!!.data)
        recyclerView.hasFixedSize()
        recyclerView.setLayoutManager( LinearLayoutManager(this));
        recyclerView.adapter = adapter
    }

    fun getDataList(key: String?):  MeterData? {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val gson = Gson()
        val json: String = prefs.getString(key!!, null).toString()

        var data =  gson.fromJson(json, MeterData().javaClass)
        return data
    }

    override fun onSupportNavigateUp(): Boolean {

        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

