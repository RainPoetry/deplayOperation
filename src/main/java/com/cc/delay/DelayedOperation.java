package com.cc.delay;

import com.cc.common.utils.timer.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * User: chenchong
 * Date: 2019/1/17
 * description:	规范延迟操作的实现标准
 *
 *
 */
public abstract class DelayedOperation extends TimerTask {

	 private AtomicBoolean completed = new AtomicBoolean(false);
	 private AtomicBoolean tryCompletePending = new AtomicBoolean(false);

	 private final Lock lock;

	 public DelayedOperation(long delayMs) {
		this(delayMs,new ReentrantLock());
	 }

	 public DelayedOperation(long delayMs, Lock lock) {
		super.delayMs = delayMs;
		this.lock = lock;
	 }

	 public boolean forceComplete() {
	 	if (completed.compareAndSet(false,true)) {
	 		cancel();
			onComplete();
			return true;
		} else {
	 		return false;
		}
	 }

	 public boolean isCompleted() {
	 	return completed.get();
	 }

	 // DelayOperation completed 之后的 回调操作
	 public abstract void onExpiration();

	 public abstract void onComplete();

	 public abstract boolean tryComplete();

	 // 尝试调用 tryComplete()
	 boolean maybeTryComplete() {
	 	boolean retry = false;
	 	boolean done = false;
	 	do {
	 		if (lock.tryLock()) {
	 			try {
					tryCompletePending.set(false);
					done = tryComplete();
				} finally {
	 				lock.unlock();
				}
				retry = tryCompletePending.get();
			} else {
	 			retry = !tryCompletePending.getAndSet(true);
			}
		} while (!isCompleted() && retry);
		 return done;
	 }

	 public void run() {
		 if (forceComplete())
			onExpiration();
	 }


}


