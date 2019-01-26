package com.cc.common.utils;

/**
 * User: chenchong
 * Date: 2019/1/18
 * description:
 */

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * An interface abstracting the clock to use in unit testing classes that make use of clock time.
 *
 * Implementations of this class should be thread-safe.
 */
public interface Time {

	Time SYSTEM = new SystemTime();

	/**
	 * Returns the current time in milliseconds.
	 */
	long milliseconds();

	/**
	 * Returns the value returned by `nanoseconds` converted into milliseconds.
	 *   将 NANOSECONDS 转换位 MILLISECONDS
	 */
	default long hiResClockMs() {
		return TimeUnit.NANOSECONDS.toMillis(nanoseconds());
	}

	/**
	 * Returns the current value of the running JVM's high-resolution time source, in nanoseconds.
	 *
	 * <p>This method can only be used to measure elapsed time and is
	 * not related to any other notion of system or wall-clock time.
	 * The value returned represents nanoseconds since some fixed but
	 * arbitrary <i>origin</i> time (perhaps in the future, so values
	 * may be negative).  The same origin is used by all invocations of
	 * this method in an instance of a Java virtual machine; other
	 * virtual machine instances are likely to use a different origin.
	 */
	long nanoseconds();

	/**
	 * Sleep for the given number of milliseconds
	 */
	void sleep(long ms);

	/**
	 * Get a timer which is bound to this time instance and expires after the given timeout
	 */
	default Timer timer(long timeoutMs) {
		return new Timer(this, timeoutMs);
	}

	/**
	 * Get a timer which is bound to this time instance and expires after the given timeout
	 */
	default Timer timer(Duration timeout) {
		return timer(timeout.toMillis());
	}
}
