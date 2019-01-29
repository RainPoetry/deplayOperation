package com.cc.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Supplier;

/**
 * User: chenchong
 * Date: 2019/1/23
 * description:
 */
public class CoreUtils {

	public static<T> T inLock(Lock lock, Supplier<? extends T> fun) {
		lock.lock();
		try {
			return fun.get();
		} finally {
			lock.unlock();
		}
	}

	public static<T> T inReadLock(ReadWriteLock lock, Supplier<? extends T> fun) {
		return inLock(lock.readLock(),fun);
	}

	public static<T> T inWriteLock(ReadWriteLock lock, Supplier<? extends T> fun) {
		return inLock(lock.writeLock(),fun);
	}

}
