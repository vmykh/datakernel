/*
 * Copyright (C) 2015 SoftIndex LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.datakernel.stream.processor;

import com.google.common.base.Function;
import io.datakernel.eventloop.Eventloop;

import java.util.HashMap;
import java.util.Iterator;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents a reducer which contains all states of accumulator during processing items.
 * It is {@link AbstractStreamMemoryTransformer} which receives original data and streams
 * changed data to destination.
 *
 * @param <K> type of keys
 * @param <I> type of input data
 * @param <O> type of output data
 * @param <A> type of accumulator
 */
public final class StreamMemoryReducer<K, I, O, A> extends AbstractStreamMemoryTransformer<I, HashMap<K, A>, O> implements StreamMemoryReducerMBean {
	private int jmxItems;

	private final StreamReducers.ReducerToResult<K, I, O, A> reducer;
	private final Function<I, K> keyFunction;
	private Iterator<A> iterator;

	/**
	 * Creates a new instance of StreamMemoryReducer
	 *
	 * @param eventloop   eventloop in which runs reducer
	 * @param reducer     reducer for processing items
	 * @param keyFunction function for searching keys
	 */
	public StreamMemoryReducer(Eventloop eventloop,
	                           StreamReducers.ReducerToResult<K, I, O, A> reducer, Function<I, K> keyFunction) {
		super(eventloop);
		this.keyFunction = checkNotNull(keyFunction);
		this.reducer = checkNotNull(reducer);

	}

	/**
	 * Creates a new storage for containing particle states of accumulator
	 */
	@Override
	protected HashMap<K, A> newState() {
		return new HashMap<>();
	}

	/**
	 * Accumulates received item and puts obtained accumulator to storage of states
	 *
	 * @param state collections which is storage of states
	 * @param item  received item
	 */
	@Override
	protected void apply(HashMap<K, A> state, I item) {
		assert jmxItems != ++jmxItems;
		K key = keyFunction.apply(item);
		A accumulator = state.get(key);
		if (accumulator == null) {
			accumulator = reducer.createAccumulator(key);
			state.put(key, accumulator);
		} else {
			A newReducerState = reducer.accumulate(accumulator, item);
			if (newReducerState != accumulator) {
				state.put(key, newReducerState);
			}
		}
	}

	/**
	 * After end of stream it creates iterator with all intermediate states.
	 *
	 * @param state collections which is storage of states
	 */
	@Override
	protected void afterEndOfStream(HashMap<K, A> state) {
		this.iterator = state.values().iterator();
	}

	/**
	 * Produces each intermediate state and streams it
	 */
	@Override
	protected void doProduce() {
		while (true) {
			if (!iterator.hasNext())
				break;
			if (status != READY)
				return;
			A accumulator = iterator.next();
			downstreamDataReceiver.onData(reducer.produceResult(accumulator));
		}
		sendEndOfStream();
	}

	@Override
	public int getItems() {
		return jmxItems;
	}

	@SuppressWarnings("AssertWithSideEffects")
	@Override
	public String toString() {
		String items = "?";
		assert (items = "" + jmxItems) != null;
		return '{' + super.toString() + " items:" + items + '}';
	}

}
