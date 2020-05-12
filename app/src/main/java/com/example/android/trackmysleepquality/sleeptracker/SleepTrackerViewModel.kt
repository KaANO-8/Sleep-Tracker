/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

    private val viewModelJob = Job()

    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private var tonight = MutableLiveData<SleepNight?>()

    private val nights = database.getAllNights()

    private val _navigateToSleepQuality = MutableLiveData<SleepNight?>()

    val navigateToSleepQuality: LiveData<SleepNight?>
        get() = _navigateToSleepQuality

    val nightsString = Transformations.map(nights) { nights ->
        formatNights(nights, application.resources)
    }

    val startButtonEnabled = Transformations.map(tonight) {
        null == it
    }

    val stopButtonEnabled = Transformations.map(tonight) {
        null != it
    }

    val clearButtonEnabled = Transformations.map(nights) {
        it?.isNotEmpty()
    }

    private var _showSnackbarEvent = MutableLiveData<Boolean>()

    val showSnackBarEvent: LiveData<Boolean>
        get() = _showSnackbarEvent

    fun doneShowingSnackbar() {
        _showSnackbarEvent.value = false
    }

    init {
        initializeTonight()
    }

    private fun initializeTonight() {
        uiScope.launch {
            tonight.value = getNightFromDatabase()
        }
    }

    private suspend fun getNightFromDatabase(): SleepNight? {
        return withContext(IO) {
            var night = database.getTonight()
            if (night?.endTimeMilli != night?.startTimeMilli)
                night = null
            night
        }
    }

    fun onStartTracking() {
        uiScope.launch {
            val newNight = SleepNight()
            insert(newNight)
            tonight.value = getNightFromDatabase()
        }
    }

    private suspend fun insert(newNight: SleepNight) {
        withContext(IO) {
            database.insert(newNight)
        }
    }

    fun onStopTracking() {
        uiScope.launch {
            val oldNight = tonight.value ?: return@launch
            oldNight.endTimeMilli = System.currentTimeMillis()
            update(oldNight)
            _navigateToSleepQuality.value = oldNight
        }
    }

    private suspend fun update(oldNight: SleepNight) {
        withContext(IO) {
            database.update(oldNight)
        }
    }

    fun onClear() {
        uiScope.launch {
            clearDatabase()
            tonight.value = null
        }
        _showSnackbarEvent.value = true
    }

    private suspend fun clearDatabase() {
        withContext(IO) {
            database.clear()
        }
    }


    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    fun doneNavigating() {
        _navigateToSleepQuality.value = null
    }
}
