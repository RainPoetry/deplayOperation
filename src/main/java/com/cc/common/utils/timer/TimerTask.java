package com.cc.common.utils.timer;

import java.util.concurrent.TimeUnit;

/**
 * User: chenchong
 * Date: 2019/1/18
 * description:
 */
public abstract class TimerTask implements Runnable {

	private TimerTaskEntry timerTaskEntry = null;
	// TimeUnit.MILLISECONDS
	protected long delayMs;

	public void cancel() {
		synchronized (this) {
			if (timerTaskEntry != null)
				timerTaskEntry.remove();
			timerTaskEntry = null;
		}
	}

	public void setTimerTaskEntry(TimerTaskEntry entry) {
		synchronized (this) {
			if (timerTaskEntry != null && timerTaskEntry != entry)
				timerTaskEntry.remove();
			timerTaskEntry = entry;
		}
	}

	public TimerTaskEntry getTimerTaskEntry() {
		return timerTaskEntry;
	}

	public long delayMs() {
		return delayMs;
	}

}
