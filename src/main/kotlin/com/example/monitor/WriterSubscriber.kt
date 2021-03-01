package com.example.monitor

import com.example.monitor.svc.MonitorData
import com.mongodb.MongoTimeoutException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

import java.util.concurrent.TimeUnit

import java.util.ArrayList
import java.util.concurrent.CountDownLatch


class WriterSubscriber : Subscriber<MonitorData> {
  private var target: String
  constructor(target: String){
    this.target = target
  }
  private val errors = mutableListOf<Throwable?>()
    private val latch = CountDownLatch(1)
    @Volatile
    private var subscription: Subscription? = null

    @Volatile
    var isCompleted = false
      private set

    override fun onSubscribe(s: Subscription?) {
      subscription = s
    }

    override fun onNext(t: MonitorData?) {
      val data = "${t?.hour},${t?.city},${t?.brand},${t?.color},\n"
      GlobalScope.launch {
        Files.write(Path.of(target), data.toByteArray(), StandardOpenOption.APPEND)
      }
    }

    override fun onError(t: Throwable?) {
      errors.add(t)
      onComplete()
    }

    override fun onComplete() {
      isCompleted = true
      latch.countDown()
    }

    @Throws(Throwable::class)
    fun await(timeout: Long = Long.MAX_VALUE, unit: TimeUnit? = TimeUnit.MILLISECONDS) {
      subscription?.request(Int.MAX_VALUE.toLong())
      if (!latch.await(timeout, unit)) {
        throw MongoTimeoutException("Publisher onComplete timed out")
      }
      if (!errors.isEmpty()) {
        throw errors[0]!!
      }
    }
}
