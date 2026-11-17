package com.minhas.teller

import android.app.Application
import android.content.Context

/**
 * Custom [Application] class for Teller.
 * Ensures critical JVM system properties for Apache POI, StAX XML parsing,
 * and Log4j 2 runtime configuration are initialized before any components or
 * services are created.
 */
class TellerApp : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        configureGlobalProperties()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        configureGlobalProperties()
        // Offline Hindi/Hinglish item dictionary (small file; load off the main thread).
        Thread { com.minhas.teller.utils.HindiItemDictionary.load(this) }.start()
        com.minhas.teller.utils.EInvoiceStore.init(this)
    }

    companion object {
        lateinit var instance: TellerApp
            private set

        init {
            configureGlobalProperties()
        }

        /**
         * Configures safe, deterministic runtime properties for:
         * 1. Aalto XML StAX Parser: Replaces standard Java XML stream factories on Android.
         * 2. Apache Log4j 2: Disables JMX, thread locals, webapp lookups, and status logging
         *    to prevent infinite recursion, reflection crashes, and StackOverflowError.
         */
        @JvmStatic
        fun configureGlobalProperties() {
            try {
                // Configure Aalto XML StAX parser for Apache POI (replaces Java XMLInputFactory)
                System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
                System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
                System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")

                // Configure Apache Log4j 2 for safe Android operation (used by Apache POI 5.x)
                System.setProperty("log4j2.statusLoggerLevel", "OFF")
                System.setProperty("log4j2.disable.jmx", "true")
                System.setProperty("log4j2.enable.threadlocals", "false")
                System.setProperty("log4j2.enable.direct.encoders", "false")
                System.setProperty("log4j2.garbagefree.threadContextMap", "false")
                System.setProperty("log4j2.is.webapp", "false")
                System.setProperty("log4j.skipJansi", "true")
                System.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level", "OFF")
            } catch (_: Throwable) {
                // Ignore any security restrictions
            }
        }
    }
}
