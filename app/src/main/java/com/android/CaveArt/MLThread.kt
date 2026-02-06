package com.android.CaveArt

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

object MLThread {
    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
}