package com.example.shammah

import org.json.JSONArray
import org.json.JSONObject

// =====================================================================================
//  BUILT-IN CHURCH DATA (With November 2026 Event Dates)
// =====================================================================================
object BuiltInData {

    fun forKey(key: String): JSONObject? = when (key) {
        "events" -> events()
        "ministries" -> ministries()
        "prayers" -> JSONObject().put("ok", true).put("prayers", JSONArray())
        else -> null
    }

    fun events(): JSONObject {
        val arr = JSONArray()
        fun add(title: String, date: String, time: String, venue: String, cat: String, desc: String, cap: Int) =
            arr.put(JSONObject().put("id", 0).put("title", title).put("date", date).put("time", time)
                .put("venue", venue).put("category", cat).put("description", desc)
                .put("capacity", cap).put("going", 12).put("my_rsvp", false))
        add("Sunday Celebration Service", "Sunday, 1 Nov 2026", "09:00 AM", "Main Sanctuary", "Worship", "Weekly celebration service.", 500)
        add("Midweek Prayer & Intercession", "Wednesday, 4 Nov 2026", "18:00 PM", "Prayer Room", "Prayer", "Midweek prayer meeting.", 200)
        add("Youth Night Encounter", "Friday, 6 Nov 2026", "18:30 PM", "Youth Hall", "Youth", "Worship & games for youth.", 150)
        add("Women of Purpose Conference", "Saturday, 7 Nov 2026", "09:00 AM", "Fellowship Hall", "Women", "Monthly Women's gathering.", 120)
        add("Community Outreach & Food Drive", "Saturday, 14 Nov 2026", "08:30 AM", "Pretoria East Campus", "Outreach", "Community outreach & food drive.", 250)
        add("November Thanksgiving Service", "Sunday, 22 Nov 2026", "09:00 AM", "Main Sanctuary", "Worship", "Thanksgiving service.", 500)
        add("Youth Leadership Summit", "Sunday, 29 Nov 2026", "14:00 PM", "Youth Centre", "Youth", "Leadership summit.", 180)
        return JSONObject().put("ok", true).put("events", arr)
    }

    fun ministries(): JSONObject {
        val arr = JSONArray()
        fun add(name: String, leader: String, schedule: String) =
            arr.put(JSONObject().put("name", name).put("leader", leader).put("schedule", schedule))
        add("Worship Ministry", "David Khumalo", "Sundays 8:00 AM · Wednesdays 6:00 PM")
        add("Youth Ministry", "Elder Nkosi", "Fridays 6:00 PM · Youth Centre")
        add("Women of Purpose", "Grace Mokoena", "Last Saturday of every month · 10:00 AM")
        add("Community Outreach", "Samuel Osei", "First Saturday of every month · 8:00 AM")
        add("Children's Ministry", "Zanele Dlamini", "Sundays 9:00 AM & 11:00 AM")
        add("Cell Groups", "Various Leaders", "Various days · Pretoria suburbs")
        add("Hospitality & Ushers", "Nomsa Sithole", "Sundays 8:30 AM · Main Entrance")
        add("Prayer Ministry", "Pastor Sithole", "Wednesdays 5:30 AM · Sundays after service")
        add("Mens Ministry", "Sipho Mahlangu", "Monthly breakfast · 1st Saturday")
        add("Media and Technical", "Sound Team Lead", "Sundays 7:30 AM")
        return JSONObject().put("ok", true).put("ministries", arr)
    }
}
