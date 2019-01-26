package com.cc.common.utils;

import javafx.util.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * User: chenchong
 * Date: 2019/1/21
 * description:	ConcurrentHashMap + Iterable
 */
public class Pool<K,V> implements Iterable<Pair<K,V>> {

	private static final int DEFAULT_SIZE = 16;
	private static final float LOAD_FACTOR = 0.75f;

	private final ConcurrentMap<K,V> pool;
	private Function<K,V> factory;

	public Pool(Function<K,V> factory) {
		this(DEFAULT_SIZE, factory);
	}

	public Pool(int size, Function<K,V> factory) {
		this(size, LOAD_FACTOR, factory);
	}

	public Pool(int size, float loadFactor, Function<K,V> factory) {
		this.pool = new ConcurrentHashMap<>(size, loadFactor);
		this.factory = factory;
	}

	public V put(K k, V v) {
		return pool.put(k,v);
	}

	public V putIfNotExists(K k, V v) {
		return pool.putIfAbsent(k,v);
	}

	public V getAndMaybePut(K key) {
		return getAndMaybePut(key, factory);
	}


	private V getAndMaybePut(K key,Function<K,V> factory) {
		return pool.computeIfAbsent(key, k->factory.apply(k));
	}

	public boolean contains(K id) {
		return pool.containsKey(id);
	}

	public V get(K key) {
		return pool.get(key);
	}

	public V remove(K key) {
		return pool.remove(key);
	}

	public boolean remove(K key, V value) {
		return pool.remove(key, value);
	}

	public Set<K> keys() {
		return pool.keySet();
	}

	public Collection<V> values() {
		return pool.values();
	}

	public void clear() {
		pool.clear();
	}

	public int size() {
		return pool.size();
	}

	@Override
	public Iterator<Pair<K,V>> iterator() {
		return new Iterator() {
			private Iterator<Map.Entry<K, V>> iterator = pool.entrySet().iterator();
			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}
			@Override
			public Pair<K,V> next() {
				Map.Entry<K,V> entry = iterator.next();
				return new Pair<K,V>(entry.getKey(),entry.getValue());
			}
		};
	}
}
