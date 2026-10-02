package com.society.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtil {

    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    fun getCurrentDate(): String {
        return dateFormat.format(Date())
    }

    fun getCurrentMonthYear(): String {
        return monthYearFormat.format(Date())
    }

    fun getMonthYearList(): List<String> {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()

        // Start from next month to allow advance payments
        cal.add(Calendar.MONTH, 1)

        // Generate next month, current month, and past 11 months (13 total)
        for (i in 0 until 13) {
            list.add(monthYearFormat.format(cal.time))
            cal.add(Calendar.MONTH, -1)
        }
        return list
    }

    fun parseMonthYearFromDate(dateStr: String): String {
        return try {
            val date = dateFormat.parse(dateStr)
            if (date != null) monthYearFormat.format(date) else getCurrentMonthYear()
        } catch (e: Exception) {
            getCurrentMonthYear()
        }
    }
}
