package com.cc.delay;

import com.cc.common.utils.Logging;
import com.cc.common.utils.Pool;
import com.cc.common.utils.ShutdownableThread;
import com.cc.common.utils.timer.SystemTimer;
import com.cc.common.utils.timer.Timer;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import static com.cc.common.utils.CoreUtils.*;

/**
 * User: chenchong
 * Date: 2019/1/21
 * description:  DelayOperation 的最外层 API 接口
 */
public class DelayedOperationPurgatory<T extends DelayedOperation> extends Logging{

	public final static int purgeInterval_default = 1000;
	private final static boolean reaperEnable_default = true;
	private final static boolean timerEnable_default = true;


	private final String purgatoryName;
	private final Timer timeoutTimer;
	public final int purgeInterval;
	private final boolean reaperEnable;
	private final boolean timerEnable;

	private Pool<Object,Watchers> watchersForKey = new Pool<>(k->new Watchers(k));
	private ReentrantReadWriteLock removeWatchersLock = new ReentrantReadWriteLock();
	private AtomicInteger estimatedTotalOperations = new AtomicInteger(0);

	// 通过该线程 来移动时间轮 并 调度 SchedulerThread.submit()
	private ExpiredOperationReaper expirationReaper;


	public DelayedOperationPurgatory(String purgatoryName) {
		this(purgatoryName, purgeInterval_default,reaperEnable_default,timerEnable_default);
	}

	public DelayedOperationPurgatory(String purgatoryName, int purgeInterval, boolean reaperEnable,
									 boolean timerEnable) {
		this(purgatoryName, new SystemTimer(purgatoryName),
				purgeInterval_default,reaperEnable_default,timerEnable_default);
	}

	public DelayedOperationPurgatory(String purgatoryName, Timer timeoutTimer , int purgeInterval,
									 boolean reaperEnable, boolean timerEnable) {
		this.purgatoryName = purgatoryName;
		this.timeoutTimer = timeoutTimer;
		this.purgeInterval = purgeInterval;
		this.reaperEnable = reaperEnable;
		this.timerEnable = timerEnable;
		this.expirationReaper = new ExpiredOperationReaper();
		if (reaperEnable)
			expirationReaper.start();
	}

	// tryComplete DelayOperation else
	public boolean tryCompleteElseWatch(T operation, List<Object> watchKeys) {
		if (watchKeys.isEmpty())
			throw new java.lang.AssertionError("assertion failed: The watch key list can't be empty");
		boolean isCompletedByMe = operation.tryComplete();
		if (isCompletedByMe)
			return true;
		boolean watchCreated = false;
		for (Object key : watchKeys) {
			if (operation.isCompleted())
				return false;
			// operation 添加到 key 映射的 watchers 中
			watchForOperation(key,operation);
			if (!watchCreated) {
				watchCreated = true;
				estimatedTotalOperations.incrementAndGet();
			}
		}
		isCompletedByMe = operation.maybeTryComplete();
		if (isCompletedByMe)
			return true;

		if (!operation.isCompleted()) {
			if (timerEnable)
				timeoutTimer.add(operation);
			if (operation.isCompleted())
				operation.cancel();
		}
		return false;
	}

	// 获取 key 对应的 watchers , 并对 watchers 内的 DelayOperation 执行 maybeTryComplete()
	public int checkAndComplete(Object key) {
		Watchers watchers = inReadLock(removeWatchersLock,()->watchersForKey.get(key));
		if (watchers == null)
			return 0;
		else
			return watchers.tryCompleteWatched();
	}

	// watchers 个数
	private Collection<Watchers> allWatchers() {
		return inReadLock(removeWatchersLock,()->watchersForKey.values());
	}

	//  所有的 watchers 中的 DelayOperation 个数
	public int watched() {
		return allWatchers().stream().map(w->w.countWatched()).reduce((a,b)->a+b).get();
	}

	// Delayed Operation 的个数
	// 也就是 添加到 DelayQueue 的个数
	public int delayed() {
		return timeoutTimer.size();
	}

	public List<T> cancelForKey(Object key) {
		return inWriteLock(removeWatchersLock,()->{
			Watchers watchers = watchersForKey.remove(key);
			if (watchers != null)
				return watchers.cancel();
			else
				return null;
		});
	}

	public boolean watchForOperation(Object key, T operation) {
		return inReadLock(removeWatchersLock,()->{
			// Watchers 不存在则新建，存在则获取
			Watchers watchers = watchersForKey.getAndMaybePut(key);
			return watchers.watch(operation);
		});
	}

	private void removeKeyIfEmpty(Object key,Watchers watchers) {
		inWriteLock(removeWatchersLock,()->{
			if (watchersForKey.get(key) != watchers)
				return Void.class;
			if (watchers != null && watchers.isEmpty())
				watchersForKey.remove(key);
			return Void.class;
		});
	}

	public void shutdown() {
		if (reaperEnable)
			expirationReaper.shutdown();
		timeoutTimer.shutdown();
	}

	public class Watchers {

		private Object key;
		// 一个 key 对应多个 DelayOperation
		private ConcurrentLinkedQueue<T> operations= new ConcurrentLinkedQueue<>();

		public Watchers(Object key) {
			this.key = key;
		}

		public int countWatched() {
			return operations.size();
		}

		public boolean isEmpty() {
			return operations.isEmpty();
		}

		public boolean watch(T t) {
			return operations.add(t);
		}

		public int tryCompleteWatched() {
			int completed = 0;
			Iterator<T> it = operations.iterator();
			while(it.hasNext()) {
				T t = it.next();
				if (t.isCompleted()) {
					it.remove();
				} else if (t.maybeTryComplete()) {
					it.remove();
					completed += 1;
				}
			}
			if (operations.isEmpty())
				removeKeyIfEmpty(key,this);
			return completed;
		}

		public List<T> cancel() {
			Iterator<T> it = operations.iterator();
			List<T> cancelled = new ArrayList<>();
			while (it.hasNext()) {
				T t = it.next();
				t.cancel();
				it.remove();
				cancelled.add(t);
			}
			return cancelled;
		}

		public int purgeCompleted() {
			int purged = 0;
			Iterator<T> it  = operations.iterator();
			while (it.hasNext()) {
				T t = it.next();
				if (t.isCompleted()) {
					it.remove();
					purged += 1;
				}
			}
			if (operations.isEmpty())
				removeKeyIfEmpty(key,this);
			return purged;
		}
	}



	public void advanceLock(long timeoutMs) {
		timeoutTimer.advanceClock(timeoutMs);
		if (estimatedTotalOperations.get() - delayed() > purgeInterval) {
			estimatedTotalOperations.getAndSet(delayed());
			debug("Begin purging watch lists");
			int purged = allWatchers().stream().map(w->w.purgeCompleted()).reduce((a,b)->a+b).get();
			debug(String.format("Purged %d elements from watch lists.",purged));
		}
	}
	private class ExpiredOperationReaper extends ShutdownableThread{

		public ExpiredOperationReaper() {
			super(String.format("ExpirationReaper-%s",purgatoryName),false);
		}
		@Override
		public void doWork() {
			advanceLock(200L);
		}
	}
}
