package com.alaimtiaz.calendaralarm

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventAdapter(
    private val context: Context,
    private var events: List<CalendarEvent>,
    private val onItemClick: (CalendarEvent) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    private val dateFmt = SimpleDateFormat("EEE d MMM yyyy — hh:mm aa", Locale("ar"))

    inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType  : TextView = view.findViewById(R.id.tvItemType)
        val tvTitle : TextView = view.findViewById(R.id.tvItemTitle)
        val tvTime  : TextView = view.findViewById(R.id.tvItemTime)
        val viewBar : View     = view.findViewById(R.id.viewItemBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        EventViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false))

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.tvTitle.text = event.title
        holder.tvTime.text  = dateFmt.format(Date(event.startTime))
        if (event.isTask) {
            holder.tvType.text = "✅ مهمة"
            holder.tvType.setBackgroundColor(context.getColor(R.color.task_green))
            holder.viewBar.setBackgroundColor(context.getColor(R.color.task_green))
        } else {
            holder.tvType.text = "📅 تقويم"
            holder.tvType.setBackgroundColor(context.getColor(R.color.accent))
            holder.viewBar.setBackgroundColor(context.getColor(R.color.accent))
        }
        holder.itemView.setOnClickListener { onItemClick(event) }
    }

    override fun getItemCount() = events.size

    fun updateEvents(newEvents: List<CalendarEvent>) { events = newEvents; notifyDataSetChanged() }
}
