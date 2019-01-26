package com.cc;

import com.cc.common.utils.Logging;
import com.cc.common.utils.Time;
import com.cc.delay.DelayedOperation;
import com.cc.delay.DelayedOperationPurgatory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * User: chenchong
 * Date: 2019/1/25
 * description:
 */
public class DelayOperationTest extends Logging {

	public DelayedOperationPurgatory<MockDelayedOperation> purgatory = null;
	public ExecutorService service = null;

	@Before
	public void setUp() {
		purgatory = new DelayedOperationPurgatory<>("mock");
	}
	@After
	public void tearDown() {
		purgatory.shutdown();
		if (service != null)
			service.shutdown();
	}

	@Test
	public void testRequestSatisfaction() {
		MockDelayedOperation r1 = new MockDelayedOperation(5000L);
		MockDelayedOperation r2 = new MockDelayedOperation(5100L);
		assertEquals("With no waiting requests, nothing should be satisfied", 0, purgatory.checkAndComplete("test1"));
		assertFalse("r1 not satisfied and hence watched", purgatory.tryCompleteElseWatch(r1, Arrays.asList("test1")));
		assertEquals("Still nothing satisfied", 0, purgatory.checkAndComplete("test1"));
		assertFalse("r2 not satisfied and hence watched", purgatory.tryCompleteElseWatch(r2, Arrays.asList("test2")));
		assertEquals("Still nothing satisfied", 0, purgatory.checkAndComplete("test2"));
		r1.completable = true;
		assertEquals("r1 satisfied", 1, purgatory.checkAndComplete("test1"));
		assertEquals("Nothing satisfied", 0, purgatory.checkAndComplete("test1"));
		r2.completable = true;
		assertEquals("r2 satisfied", 1, purgatory.checkAndComplete("test2"));
		assertEquals("Nothing satisfied", 0, purgatory.checkAndComplete("test2"));
	}

	@Test
	public void testRequestExpiry() throws InterruptedException {
		long expiration = 20L;
		long startMs = Time.SYSTEM.hiResClockMs();
		MockDelayedOperation r1 = new MockDelayedOperation(expiration);
		MockDelayedOperation r2 = new MockDelayedOperation(200000L);
		assertFalse("r1 not satisfied and hence watched", purgatory.tryCompleteElseWatch(r1, Arrays.asList("test1")));
		Thread.sleep(1000);
		assertTrue("r1 completed due to expiration", r1.isCompleted());
		assertFalse("r2 hasn't completed", r2.isCompleted());
	}

	@Test
	public void  testRequestPurge() {
		MockDelayedOperation r1 = new MockDelayedOperation(100000L);
		MockDelayedOperation r2 = new MockDelayedOperation(100000L);
		MockDelayedOperation r3 = new MockDelayedOperation(100000L);
		purgatory.tryCompleteElseWatch(r1, Arrays.asList("test1"));
		purgatory.tryCompleteElseWatch(r2, Arrays.asList("test1", "test2"));
		purgatory.tryCompleteElseWatch(r3, Arrays.asList("test1", "test2", "test3"));

		assertEquals("Purgatory should have 3 total delayed operations", 3, purgatory.delayed());
		assertEquals("Purgatory should have 6 watched elements", 6, purgatory.watched());

		// complete the operations, it should immediately be purged from the delayed operation
		r2.completable = true;
		r2.tryComplete();
		assertEquals("Purgatory should have 2 total delayed operations instead of " + purgatory.delayed(), 2, purgatory.delayed());

		r3.completable = true;
		r3.tryComplete();
		assertEquals("Purgatory should have 1 total delayed operations instead of " + purgatory.delayed(), 1, purgatory.delayed());

		// checking a watch should purge the watch list

		System.out.println(purgatory.watched());
		assertEquals("Purgatory should have 4 watched elements instead of " + purgatory.watched(), 4, purgatory.watched());

		purgatory.checkAndComplete("test2");
		assertEquals("Purgatory should have 2 watched elements instead of " + purgatory.watched(), 2, purgatory.watched());

		purgatory.checkAndComplete("test3");
		assertEquals("Purgatory should have 1 watched elements instead of " + purgatory.watched(), 1, purgatory.watched());
	}

	@Test
	public void  shouldCancelForKeyReturningCancelledOperations() {
		purgatory.tryCompleteElseWatch(new MockDelayedOperation(10000L), Arrays.asList("key"));
		purgatory.tryCompleteElseWatch(new MockDelayedOperation(10000L),  Arrays.asList("key"));
		purgatory.tryCompleteElseWatch(new MockDelayedOperation(10000L),  Arrays.asList("key2"));

		List<MockDelayedOperation> cancelledOperations = purgatory.cancelForKey("key");
		assertEquals(2, cancelledOperations.size());
		assertEquals(1, purgatory.delayed());
		assertEquals(1, purgatory.watched());
	}


	// 多个线程调度 一个 DelayOperation.maybeTryComplete(), 产生的锁问题
	@Test
	public void testTryCompleteLockContention() throws InterruptedException, ExecutionException, TimeoutException {
		service = Executors.newSingleThreadExecutor();
		AtomicInteger completionAttemptsRemaining = new AtomicInteger(Integer.MAX_VALUE);
		Semaphore tryCompleteSemaphore = new Semaphore(1);
		String key = "key";
		MockDelayedOperation op = new MockDelayedOperation(10000L) {
			@Override
			public boolean tryComplete()  {
				boolean shouldComplete = completionAttemptsRemaining.decrementAndGet() <= 0;
				try {
					error("begin: " + tryCompleteSemaphore.availablePermits());
					tryCompleteSemaphore.acquire();
					info("get lock");
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				try {
					if (shouldComplete)
						return forceComplete();
					else
						return false;
				} finally {
					tryCompleteSemaphore.release();
					info("unlock");
					System.out.println("release: " + tryCompleteSemaphore.availablePermits());
				}
			}
		};
		purgatory.tryCompleteElseWatch(op, Arrays.asList(key));
		completionAttemptsRemaining.set(2);
		tryCompleteSemaphore.acquire();
		// 发送一个异步请求
		Future<Integer> future = runOnAnotherThread(()->purgatory.checkAndComplete(key), false);
		// 等待上面的线程获得锁
		while(!tryCompleteSemaphore.hasQueuedThreads()){}
		// 由于前面的线程获得了锁，并且等待 Semaphore 资源释放
		//  因此，当前下才能无法获得 lock ，也就无法执行 tryCOmplete() 操作，直接进入下一步
		int counts = purgatory.checkAndComplete(key); // this should not block even though lock is not free

		assertFalse("Operation should not have completed", op.isCompleted());
		// Semaphore 释放资源，导致前面线程进入 tryComplete() 执行相关操作
		tryCompleteSemaphore.release();

		int result = future.get();
		assertTrue("Operation should have completed", op.isCompleted());
	}

	@Test
	public void  testTryCompleteWithMultipleThreads(){
		ScheduledExecutorService service = Executors.newScheduledThreadPool(20);
		Random random = new Random();
		int maxDelayMs = 10;
		int completionAttempts = 20;
		class TestDelayOperation extends MockDelayedOperation {

			private final String key;
			AtomicInteger completionAttemptsRemaining = new AtomicInteger(completionAttempts);
			public TestDelayOperation(int index) {
				super(10000L);
				this.key = "key:"+index;
			}

			public boolean tryComplete() {
				boolean shouldComplete = completable;
				try {
					Thread.sleep(random.nextInt(maxDelayMs));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if (shouldComplete)
					return forceComplete();
				else
					return false;
			}
		}

		List<TestDelayOperation> lists = new ArrayList<>();
		for(int i=0;i<100;i++) {
			TestDelayOperation  op = new TestDelayOperation(i);
			purgatory.tryCompleteElseWatch(op,Arrays.asList(op.key));
			lists.add(op);
		}

	}

	private<T> Future<T> runOnAnotherThread(Supplier<T> supplier, Boolean shouldComplete) throws ExecutionException, InterruptedException {
		Future<T> future = service.submit(()->supplier.get());
		if (!shouldComplete)
			assertFalse("Should not have completed", future.isDone());
		return future;
	}

	class MockDelayedOperation extends DelayedOperation{
		boolean completable = false;

		public MockDelayedOperation(long delayMS) {
			super(delayMS);
		}

		@Override
		public void onExpiration() {
			System.out.println("expiration deal");
		}

		@Override
		public void onComplete() {
			System.out.println("complete deal");
		}

		@Override
		public boolean tryComplete() {
			if (completable)
				return forceComplete();
			return false;
		}
	}
}
