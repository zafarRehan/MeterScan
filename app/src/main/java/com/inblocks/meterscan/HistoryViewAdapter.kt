package com.inblocks.meterscan


import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineStart
import kotlin.io.encoding.Base64

class HistoryViewAdapter() : RecyclerView.Adapter<HistoryViewAdapter.ViewHolder>() {

    var list =  ArrayList<MeterDataModel>()
    constructor(list: ArrayList<MeterDataModel>) : this() {
       this.list = list
   }



    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val unit: TextView = itemView.findViewById(R.id.unit)
        val unitPer: TextView = itemView.findViewById(R.id.unitConf)
        val meterValue: TextView = itemView.findViewById(R.id.value)
        val valuePer: TextView = itemView.findViewById(R.id.valueConf)
        val dedected : TextView =  itemView.findViewById(R.id.mdDetected)
        val dedectedPer : TextView =  itemView.findViewById(R.id.mdDetectedConf)
        val image : ImageView =  itemView.findViewById(R.id.displayLCD)


    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.history_item_view, parent, false)
        )
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        holder.unit.text = list[position].unit
        holder.unitPer.text = list[position].unitPer
        holder.meterValue.text = list[position].reading
        holder.valuePer.text = list[position].readingPer
        holder.dedected.text = list[position].maxDemand
        holder.dedectedPer.text = list[position].demandPer
        if(list[position].meterImage !=null) {
            val imageBytes =
                android.util.Base64.decode(list[position].meterImage, android.util.Base64.DEFAULT)
            val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            holder.image.setImageBitmap(decodedImage)
        }


    }
}