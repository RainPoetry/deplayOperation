package com.cc.common.utils.timer;

import com.cc.common.utils.Time;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: chenchong
 * Date: 2019/1/17
 * description:	时间轮
 */


/*
 * Hierarchical Timing Wheels (分层时间轮)
 *
 * 插入、删除的时间复杂度为 O(1)
 * DealyQueue 和 Timer 的插入和删除的时间复杂度为 O(log n)
 *
 * A simple timing wheel is a circular list of buckets of timer tasks. Let u be the time unit.
 * A timing wheel with size n has n buckets and can hold timer tasks in n * u time interval.
 * Each bucket holds timer tasks that fall into the corresponding time range. At the beginning,
 * the first bucket holds tasks for [0, u), the second bucket holds tasks for [u, 2u), …,
 * the n-th bucket for [u * (n -1), u * n). Every interval of time unit u, the timer ticks and
 * moved to the next bucket then expire all timer tasks in it. So, the timer never insert a task
 * into the bucket for the current time since it is already expired. The timer immediately runs
 * the expired task. The emptied bucket is then available for the next round, so if the current
 * bucket is for the time t, it becomes the bucket for [t + u * n, t + (n + 1) * u) after a tick.
 * A timing wheel has O(1) cost for insert/delete (start-timer/stop-timer) whereas priority queue
 * based timers, such as java.util.concurrent.DelayQueue and java.util.Timer, have O(log n)
 * insert/delete cost.
 *
 * A major drawback of a simple timing wheel is that it assumes that a timer request is within
 * the time interval of n * u from the current time. If a timer request is out of this interval,
 * it is an overflow. A hierarchical timing wheel deals with such overflows. It is a hierarchically
 * organized timing wheels. The lowest level has the finest time resolution. As moving up the
 * hierarchy, time resolutions become coarser(粗糙). If the resolution of a wheel at one level is u and
 * the size is n, the resolution of the next level should be n * u. At each level overflows are
 * delegated to the wheel in one level higher. When the wheel in the higher level ticks, it reinsert
 * timer tasks to the lower level. An overflow wheel can be created on-demand. When a bucket in an
 * overflow bucket expires, all tasks in it are reinserted into the timer recursively. The tasks
 * are then moved to the finer grain wheels or be executed. The insert (start-timer) cost is O(m)
 * where m is the number of wheels, which is usually very small compared to the number of requests
 * in the system, and the delete (stop-timer) cost is still O(1).
 *
 * Example
 * Let's say that u is 1 and n is 3. If the start time is c,
 * then the buckets at different levels are:
 *
 * level    buckets
 * 1        [c,c]   [c+1,c+1]  [c+2,c+2]
 * 2        [c,c+2] [c+3,c+5]  [c+6,c+8]
 * 3        [c,c+8] [c+9,c+17] [c+18,c+26]
 *
 * The bucket expiration is at the time of bucket beginning.
 * So at time = c+1, buckets [c,c], [c,c+2] and [c,c+8] are expired.
 * Level 1's clock moves to c+1, and [c+3,c+3] is created.
 * Level 2 and level3's clock stay at c since their clocks move in unit of 3 and 9, respectively.
 * So, no new buckets are created in level 2 and 3.
 *
 * Note that bucket [c,c+2] in level 2 won't receive any task since that range is already covered in level 1.
 * The same is true for the bucket [c,c+8] in level 3 since its range is covered in level 2.
 * This is a bit wasteful, but simplifies the implementation.
 *
 * 1        [c+1,c+1]  [c+2,c+2]  [c+3,c+3]
 * 2        [c,c+2]    [c+3,c+5]  [c+6,c+8]
 * 3        [c,c+8]    [c+9,c+17] [c+18,c+26]
 *
 * At time = c+2, [c+1,c+1] is newly expired.
 * Level 1 moves to c+2, and [c+4,c+4] is created,
 *
 * 1        [c+2,c+2]  [c+3,c+3]  [c+4,c+4]
 * 2        [c,c+2]    [c+3,c+5]  [c+6,c+8]
 * 3        [c,c+8]    [c+9,c+17] [c+18,c+18]
 *
 * At time = c+3, [c+2,c+2] is newly expired.
 * Level 2 moves to c+3, and [c+5,c+5] and [c+9,c+11] are created.
 * Level 3 stay at c.
 *
 * 1        [c+3,c+3]  [c+4,c+4]  [c+5,c+5]
 * 2        [c+3,c+5]  [c+6,c+8]  [c+9,c+11]
 * 3        [c,c+8]    [c+9,c+17] [c+8,c+11]
 *
 * The hierarchical timing wheels works especially well when operations are completed before they time out.
 * Even when everything times out, it still has advantageous when there are many items in the timer.
 * Its insert cost (including reinsert) and delete cost are O(m) and O(1), respectively while priority
 * queue based timers takes O(log N) for both insert and delete where N is the number of items in the queue.
 *
 * This class is not thread-safe. There should not be any add calls while advanceClock is executing.
 * It is caller's responsibility to enforce it. Simultaneous add calls are thread-safe.
 */
public class TimingWheel {

	private final long tickMs;
	private final int wheelSize;
	private final long startMs;
	private final AtomicInteger taskCounter;
	private final DelayQueue<TimerTaskList> queue;

	private final long interval;
	private final TimerTaskList[] buckets;
	private long currentTime;
	private volatile TimingWheel overflowWheel;

	public TimingWheel(long tickMs, int wheelSize, long startMs, AtomicInteger taskCounter,
					   DelayQueue<TimerTaskList> queue) {
		this.tickMs = tickMs;
		this.wheelSize = wheelSize;
		this.startMs = startMs;
		this.taskCounter = taskCounter;
		this.queue = queue;

		this.interval = tickMs * wheelSize;
		this.buckets = new TimerTaskList[wheelSize];
		this.currentTime = startMs - (startMs % tickMs);

		for(int i = 0; i < buckets.length;) {
			buckets[i++] = new TimerTaskList(taskCounter);
		}
	}

	private void addOverflowWheel() {
		synchronized (this) {
			if (overflowWheel == null) {
				overflowWheel = new TimingWheel(interval, wheelSize, currentTime, taskCounter, queue);
			}
		}
	}

	public boolean add(TimerTaskEntry timerTaskEntry) {
		long expiration = timerTaskEntry.expirationMs();
		if (timerTaskEntry.cancel()) {
			return false;
			// expiration 是否 < 当前 bucket 的最大时间
			// 当 Task 为于高层时间轮时，其 tickMs >>> ticks ， 因此，该 Task 会进行降层，插入到低层的时间轮中
			// (判断该 Task 是否过期是以 最底层的 时间轮为标准的)
		} else if (expiration < currentTime + tickMs) {
			// Already expired
			return false;
		} else if (expiration < currentTime + interval) {
			// 保证当有新的 Task 进来时，原来 bucket 的过期时间不会改变
			long virtualId = expiration / tickMs;
			int index = (int)(virtualId % wheelSize);
			TimerTaskList bucket = buckets[index];
			bucket.add(timerTaskEntry);
			// 设置 bucket 的过期时间, 并将 bucket 添加到 DelayQueue
			if (bucket.setExpiration(virtualId * tickMs)) {
				queue.offer(bucket);
			}
			return true;
		} else {
			if (overflowWheel == null)
				addOverflowWheel();
			return overflowWheel.add(timerTaskEntry);
		}
	}

	// Try to advance the clock
	public void advanceClock(long timeMs) {

		if (timeMs >= currentTime + tickMs) {
			currentTime =  timeMs - (timeMs % tickMs);
//			System.out.println("advance: " + timeMs + " - " + currentTime);
			if (overflowWheel != null)
				overflowWheel.advanceClock(currentTime);
		}
	}

}
