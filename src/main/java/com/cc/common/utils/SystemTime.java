package com.cc.common.utils;

/**
 * User: chenchong
 * Date: 2019/1/21
 * description:
 */

/**
 * A time implementation that uses the system clock and sleep call. Use `Time.SYSTEM` instead of creating an instance
 * of this class.
 */
public class SystemTime implements Time {

	@Override
	public long milliseconds() {
		return System.currentTimeMillis();
	}

	@Override
	public long nanoseconds() {
		return System.nanoTime();
	}

	@Override
	public void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			// just wake up early
			Thread.currentThread().interrupt();
		}
	}

}

