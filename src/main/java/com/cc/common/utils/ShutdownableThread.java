package com.cc.common.utils;

import com.cc.common.internals.FatalExitError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * User: chenchong
 * Date: 2019/1/23
 * description:
 */
public abstract class ShutdownableThread extends Thread  {

		private final Logger logger = LoggerFactory.getLogger(getClass().getName());
		private final String name;
		private final boolean isInterruptible;
		private final String logIdent;

		private CountDownLatch shutdownInitiated = new CountDownLatch(1);
		private CountDownLatch shutdownComplete = new CountDownLatch(1);

		public ShutdownableThread(String name, boolean isInterruptible) {
			super(name);
			setDaemon(false);
			this.logIdent = "["+name+"]: ";
			this.name = name;
			this.isInterruptible = isInterruptible;
		}

		public void shutdown() {
			initiateShutdown();
			awaitShutdown();
		}

		public boolean isShutdownComplete() {
			return shutdownComplete.getCount() == 0;
		}

		public boolean initiateShutdown() {
			synchronized (this) {
				if (isRunning()) {
					logger.info(msg("Shutting down"));
					shutdownInitiated.countDown();
					if (isInterruptible)
						interrupt();
					return true;
				} else {
					return false;
				}
			}
		}

		public void awaitShutdown() {
			try {
				shutdownComplete.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			logger.info(msg("Shutdown Complete"));
		}

		public void pause(long timeout, TimeUnit unit) {
			try {
				if (shutdownInitiated.await(timeout,unit))
					logger.trace(msg("shutdownInitiated latch count reached zero, Shutdown called."));

			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public abstract void doWork();

		public boolean isRunning() {
			return shutdownInitiated.getCount() != 0;
		}

		public void run() {
			logger.info("starting");
			try {
				while (isRunning())
					doWork();
			} catch (FatalExitError error) {
				shutdownInitiated.countDown();
				shutdownComplete.countDown();
				logger.info(msg("Stopped"));
				System.exit(error.statusCode());
			} catch (Throwable t) {
				if (isRunning())
					logger.error(msg("Error due to"),t);
			} finally {
				shutdownComplete.countDown();
			}
			logger.info(msg("Stopped"));
		}

		private String msg(String info) {
			return logIdent + info;
		}
}
