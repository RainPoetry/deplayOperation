package com.cc.delayOperation;

import com.cc.delay.DelayedOperation;
import com.cc.delay.DelayedOperationPurgatory;

import java.util.Arrays;

/**
 * User: chenchong
 * Date: 2019/1/24
 * description:
 */
public class DelayPrint extends DelayedOperation {

	boolean completable = false;

	public DelayPrint(long delayMs) {
		super(delayMs);
	}

	@Override
	public void onExpiration() {
		System.out.println(" expiration operation");
	}

	@Override
	public void onComplete() {
		System.out.println("need to complete the operation");
	}

	@Override
	public boolean tryComplete() {
		if (completable)
			return forceComplete();
		return false;
	}


	public static void main(String[] args){
		DelayPrint print = new DelayPrint(5004L);
		DelayPrint print2 = new DelayPrint(5005L);
		DelayedOperationPurgatory<DelayPrint> delayPrintPurgatory = new DelayedOperationPurgatory<>("print");
		delayPrintPurgatory.tryCompleteElseWatch(print, Arrays.asList("myDemo"));
		delayPrintPurgatory.tryCompleteElseWatch(print2, Arrays.asList("myDemo2"));
	}
}
