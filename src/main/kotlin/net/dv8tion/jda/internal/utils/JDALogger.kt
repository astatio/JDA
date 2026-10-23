/*
 * Copyright 2015 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.internal.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLogger
import org.slf4j.spi.SLF4JServiceProvider
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.HashMap
import java.util.ServiceLoader
import javax.annotation.Nonnull

/**
 * This class serves as a LoggerFactory for JDA's internals.
 * <br>It will either return a Logger from a SLF4J implementation via [org.slf4j.LoggerFactory] if present,
 * or an instance of a custom [FallbackLogger].
 *
 * It also has the utility method [.getLazyString] which is used to lazily construct Strings for Logging.
 *
 * @see .setFallbackLoggerEnabled
 */
object JDALogger {
    /**
     * The name of the system property, which controls whether the fallback logger is disabled.
     */
    const val DISABLE_FALLBACK_PROPERTY_NAME: String = "net.dv8tion.jda.disableFallbackLogger"

    /**
     * Whether an implementation of [SLF4JServiceProvider] was found.
     * <br>If false, JDA will use its fallback logger.
     *
     * The fallback logger can be disabled with [.setFallbackLoggerEnabled]
     * or using the system property [.DISABLE_FALLBACK_PROPERTY_NAME].
     */
    @JvmField
    val SLF4J_ENABLED: Boolean

    private var disableFallback: Boolean = java.lang.Boolean.getBoolean(DISABLE_FALLBACK_PROPERTY_NAME)
    private val fallbackLoggerConstructor: MethodHandle?

    init {
        var hasLoggerImpl = false
        try {
            val provider = Class.forName("org.slf4j.spi.SLF4JServiceProvider")
            hasLoggerImpl = ServiceLoader.load(provider).iterator().hasNext()
        } catch (ignored: ClassNotFoundException) {
            disableFallback = true // only works with SLF4J 2.0+
        }

        SLF4J_ENABLED = hasLoggerImpl

        // Dynamically load fallback logger to avoid static initializer errors

        var constructor: MethodHandle? = null
        try {
            val lookup = MethodHandles.publicLookup()
            val fallbackLoggerClass = Class.forName("net.dv8tion.jda.internal.utils.FallbackLogger")
            constructor =
                lookup.findConstructor(fallbackLoggerClass, MethodType.methodType(Void.TYPE, String::class.java))
        } catch (ignored: ClassNotFoundException) {
        } catch (ignored: ExceptionInInitializerError) {
        } catch (ignored: IllegalAccessException) {
        } catch (ignored: NoClassDefFoundError) {
        } catch (ignored: NoSuchMethodException) {
        }

        fallbackLoggerConstructor = constructor
    }

    private val LOGS: MutableMap<String, Logger> = HashMap()

    /**
     * Disables the automatic fallback logger that JDA uses when no SLF4J implementation is found.
     *
     * @param enabled
     *        False, to disable the fallback logger
     */
    @JvmStatic
    fun setFallbackLoggerEnabled(enabled: Boolean) {
        disableFallback = !enabled
    }

    /**
     * Will get the [org.slf4j.Logger] with the given log-name
     * or create and cache a fallback logger if there is no SLF4J implementation present.
     *
     * The fallback logger uses a constant logging configuration and prints directly to [System.err].
     *
     * @param name
     *         The name of the Logger
     *
     * @return Logger with given log name
     */
    @Nonnull
    @JvmStatic
    fun getLog(name: String): Logger =
        synchronized(LOGS) {
            if (SLF4J_ENABLED || disableFallback) {
                return LoggerFactory.getLogger(name)
            }
            newFallbackLogger(name)
        }

    /**
     * Will get the [org.slf4j.Logger] for the given Class
     * or create and cache a fallback logger if there is no SLF4J implementation present.
     *
     * The fallback logger uses a constant logging configuration and prints directly to [System.err].
     *
     * @param clazz
     *         The class used for the Logger name
     *
     * @return Logger for given Class
     */
    @Nonnull
    @JvmStatic
    fun getLog(clazz: Class<*>): Logger =
        synchronized(LOGS) {
            if (SLF4J_ENABLED || disableFallback) {
                return LoggerFactory.getLogger(clazz)
            }
            newFallbackLogger(clazz.simpleName)
        }

    private fun printFallbackWarning() {
        val logger = newFallbackLogger(JDALogger::class.java.simpleName)
        logger.warn("Using fallback logger due to missing SLF4J implementation.")
        logger.warn("Please setup a logging framework to use JDA.")
        logger.warn("You can use our logging setup guide https://jda.wiki/setup/logging/")
        logger.warn(
            "To disable the fallback logger, add the slf4j-nop dependency or use JDALogger.setFallbackLoggerEnabled(false)",
        )
    }

    // Mirrors the original Java control flow, where each branch returns directly.
    // The Java original also caught Throwable and printed the trace verbatim.
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private fun newFallbackLogger(name: String): Logger {
        if (disableFallback || fallbackLoggerConstructor == null) {
            return NOPLogger.NOP_LOGGER
        }

        try {
            synchronized(LOGS) {
                if (LOGS.containsKey(name)) {
                    return LOGS[name]!!
                }
                val logger = fallbackLoggerConstructor.invoke(name) as Logger
                val isFirstFallback = LOGS.isEmpty()
                LOGS[name] = logger
                if (isFirstFallback) {
                    printFallbackWarning()
                }
                return logger
            }
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to initialize fallback logger", e)
        }
    }

    /**
     * Utility function to enable logging of complex statements more efficiently (lazy).
     *
     * @param lazyLambda
     *         The Supplier used when evaluating the expression
     *
     * @return An Object that can be passed to SLF4J's logging methods as lazy parameter
     */
    @Nonnull
    @JvmStatic
    fun getLazyString(lazyLambda: LazyEvaluation): Any =
        object : Any() {
            // The Java original caught Exception and wrote the stack trace to a StringWriter
            // so a broken lazy evaluation cannot itself throw from toString().
            @Suppress("TooGenericExceptionCaught", "PrintStackTrace")
            override fun toString(): String =
                try {
                    lazyLambda.getString()
                } catch (ex: Exception) {
                    val sw = StringWriter()
                    ex.printStackTrace(PrintWriter(sw))
                    "Error while evaluating lazy String... $sw"
                }
        }

    /**
     * Functional interface used for [.getLazyString] to lazily construct a String.
     */
    fun interface LazyEvaluation {
        /**
         * This method is used by [.getLazyString]
         * when SLF4J requests String construction.
         * <br>The String returned by this is used to construct the log message.
         *
         * @throws Exception
         *         To allow lazy evaluation of methods that might throw exceptions
         *
         * @return The String for log message
         */
        @Throws(Exception::class)
        fun getString(): String
    }
}
