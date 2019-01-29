package com.cc.common.utils.timer;

import com.cc.common.utils.Time;
import com.cc.delayOperation.DelayPrint;

import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * User: chenchong
 * Date: 2019/1/17
 * description:	数据存储的实现 ， 双向链表
 */
public class TimerTaskList implements Delayed {

	public final AtomicInteger taskCounter;

	private TimerTaskEntry root = new TimerTaskEntry(null, -1);
	private AtomicLong expiration = new AtomicLong(-1L);

	public TimerTaskList(AtomicInteger taskCounter) {
		this.taskCounter = taskCounter;
		root.next = root;
		root.prev = root;
	}

	public boolean setExpiration(long expirationMs) {
		return expiration.getAndSet(expirationMs) != expirationMs;
	}

	public Long getExpiration() {
		return expiration.get();
	}

	public void forEach(Consumer<? super TimerTask> action) {
		Objects.requireNonNull(action);
		synchronized (this) {
			TimerTaskEntry entry = root.next;
			while (entry != root) {
				TimerTaskEntry nextEntry = entry.next;
				if (!entry.cancel())
					action.accept(entry.timerTask());
				entry = nextEntry;
			}
		}
	}

	public void add(TimerTaskEntry timerTaskEntry) {
		boolean done = false;
		while (!done) {
			timerTaskEntry.remove();
			synchronized (timerTaskEntry) {
				if (timerTaskEntry.list == null) {
					TimerTaskEntry tail = root.prev;
					timerTaskEntry.next = root;
					timerTaskEntry.prev = tail;
					timerTaskEntry.list = this;
					tail.next = timerTaskEntry;
					root.prev = timerTaskEntry;
					taskCounter.incrementAndGet();
					done = true;
				}
			}
		}
	}

	public void flush(Consumer<? super TimerTaskEntry> action) {
		synchronized (this) {
			TimerTaskEntry head = root.next;
			while (head != root) {
				remove(head);
//				System.out.println("删除 head : " + head.toString());
				// 当 Task 为于高层时间轮时，其 tickMs >> ticks ， 因此，该 Task 会进行降层(也有可能过期)，插入到低层的时间轮中
				// 父级的 tickMs = 当前层的 tikcMS * wheelSize
				action.accept(head);
				head = root.next;
			}
			expiration.set(-1L);
		}
	}


	@Override
	public long getDelay(TimeUnit unit) {
		return unit.convert(Math.max(getExpiration()- Time.SYSTEM.hiResClockMs(),0), TimeUnit.MILLISECONDS);
	}

	@Override
	public int compareTo(Delayed o) {
		TimerTaskList timer = (TimerTaskList) o;
		if (getExpiration() < timer.getExpiration())
			return -1;
		else if (getExpiration() > timer.getExpiration())
			return 1;
		else
			return 0;
	}

 	public void remove(TimerTaskEntry timerTaskEntry) {
		synchronized (this) {
			if (timerTaskEntry.list.equals(this)) {
				timerTaskEntry.next.prev = timerTaskEntry.prev;
				timerTaskEntry.prev.next = timerTaskEntry.next;
				timerTaskEntry.next = null;
				timerTaskEntry.prev = null;
				timerTaskEntry.list = null;
				taskCounter.decrementAndGet();
			}
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
			TimerTaskEntry entry = root.next;
			while (entry != root) {
				sb.append(entry.toString());
				TimerTaskEntry nextEntry = entry.next;
				entry = nextEntry;
			}
		return "TimerTaskList:"+sb.toString()+"";
	}
}
