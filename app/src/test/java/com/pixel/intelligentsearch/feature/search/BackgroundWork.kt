package com.pixel.intelligentsearch.feature.search

import android.os.Looper
import com.pixel.intelligentsearch.di.DefaultDispatcher
import com.pixel.intelligentsearch.di.DispatchersModule
import com.pixel.intelligentsearch.di.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import org.robolectric.Shadows.shadowOf

private val running = AtomicInteger()

/** Runs on [dispatcher] as usual, counting each task from the moment it is handed over until it has ended. */
private class CountingDispatcher(private val dispatcher: CoroutineDispatcher) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        running.incrementAndGet()
        dispatcher.dispatch(context, Runnable {
            try {
                block.run()
            } finally {
                running.decrementAndGet()
            }
        })
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DispatchersModule::class])
object CountingDispatchersModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = CountingDispatcher(Dispatchers.IO)

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = CountingDispatcher(Dispatchers.Default)
}

/**
 * Runs what the search has started to its end, for checking that something did NOT happen: moves the main looper's
 * clock past the suggestion pacing and waits for the injected dispatchers, until neither has work left. However slow
 * the machine, a request that was going to go out has gone out by the time this returns. [held] is how many tasks the
 * test is holding on purpose. Not counted: real-time `delay`s, and code on a dispatcher that isn't injected.
 */
fun finishBackgroundWork(held: Int = 0) {
    val looper = shadowOf(Looper.getMainLooper())
    val deadline = System.currentTimeMillis() + 10_000
    do {
        looper.idleFor(Duration.ofMillis(500))
        while (running.get() > held) {
            check(System.currentTimeMillis() < deadline) { "background work still running: ${running.get()} tasks" }
            Thread.sleep(1)
        }
    } while (!looper.isIdle)
}

/** Work runs on real threads and on the main looper, whose delays only elapse as its clock is moved. */
fun SearchViewModel.awaitState(what: String, condition: (SearchUiState) -> Boolean): SearchUiState {
    val deadline = System.currentTimeMillis() + 10_000
    while (System.currentTimeMillis() < deadline) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        val state = uiState.value
        if (condition(state)) return state
        Thread.sleep(25)
    }
    throw AssertionError("timed out waiting for $what; state=${uiState.value}")
}
