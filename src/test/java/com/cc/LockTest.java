package com.cc;

import org.junit.Test;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import static com.cc.common.utils.CoreUtils.*;
import static com.cc.common.utils.CoreUtils.inWriteLock;

/**
 * User: chenchong
 * Date: 2019/1/23
 * description:
 */
public class LockTest {

	private static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private int count = 0;

	@Test
	public void demo(){

		new Thread(()->{
			deal();
		}).start();
		new Thread(()->{
			deal2();
		}).start();
	}

	public static void deal() {
		inWriteLock(lock,()->{
			System.out.println("hello");
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("over");
			return Void.class;
		});
	}

	public static void deal2() {
		inReadLock(lock,()->{
			System.out.println("hello2222222222");
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("deal2222  over");
			return Void.class;
		});
	}
}
