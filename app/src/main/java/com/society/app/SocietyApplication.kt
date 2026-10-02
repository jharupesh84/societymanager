package com.society.app

import android.app.Application
import com.society.app.data.local.SocietyDatabase
import com.society.app.data.repository.SocietyRepository

class SocietyApplication : Application() {
    val database by lazy { SocietyDatabase.getDatabase(this) }
    val repository by lazy { SocietyRepository(database.societyDao()) }
}
