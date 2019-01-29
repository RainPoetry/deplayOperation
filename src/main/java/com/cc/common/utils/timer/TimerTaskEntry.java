package com.cc.common.utils.timer;

/**
 * User: chenchong
 * Date: 2019/1/18
 * description:	 数据单元
 */
public class TimerTaskEntry {

	public TimerTaskList list = null;
	public TimerTaskEntry next = null;
	public TimerTaskEntry prev = null;

	private final TimerTask timerTask;
	//  TimeUnit.MILLISECONDS = 当前时间 + delayMs
	private final Long expirationMs;

	public TimerTaskEntry(TimerTask timerTask, long expirationMs) {
		this.expirationMs = expirationMs;
		this.timerTask = timerTask;
		if (timerTask != null)
			timerTask.setTimerTaskEntry(this);
	}

	public boolean cancel() {
		return timerTask.getTimerTaskEntry() != this;
	}

	public TimerTask timerTask() {
		return timerTask;
	}

	public Long expirationMs() {
		return expirationMs;
	}

	public void remove() {
		TimerTaskList currentList = list;
		while (currentList != null) {
			currentList.remove(this);
			currentList = list;
		}
	}

	public int compare(TimerTaskEntry entry) {
		return this.expirationMs.compareTo(entry.expirationMs);
	}

	@Override
	public String toString() {
		return "TimerTaskEntry{expirationMs:"+expirationMs+"}";
	}
}
