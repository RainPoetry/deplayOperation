package com.cc.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: chenchong
 * Date: 2019/1/21
 * description:	任务调度线程
 */
public class SchedulerThread extends Thread {

	private final Logger log = LoggerFactory.getLogger(getClass());

	public static SchedulerThread daemon(final String name, Runnable runnable) {
		return new SchedulerThread(name, runnable, true);
	}

	public static SchedulerThread nonDaemon(final String name, Runnable runnable) {
		return new SchedulerThread(name, runnable, false);
	}

	public SchedulerThread(final String name, boolean daemon) {
		super(name);
		configureThread(name, daemon);
	}

	public SchedulerThread(final String name, Runnable runnable, boolean daemon) {
		super(runnable, name);
		configureThread(name, daemon);
	}

	private void configureThread(final String name, boolean daemon) {
		setDaemon(daemon);
		setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, Throwable e) {
				log.error("Uncaught exception in thread '{}':", name, e);
			}
		});
	}
}
