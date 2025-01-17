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

package io.datakernel.cube;

import com.google.common.base.Function;
import io.datakernel.async.CompletionCallback;
import io.datakernel.async.ResultCallback;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.stream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.google.common.collect.Iterables.transform;

public final class AggregationGroupReducer<T> extends AbstractStreamConsumer<T> implements StreamDataReceiver<T> {
	private static final Logger logger = LoggerFactory.getLogger(AggregationGroupReducer.class);

	private final AggregationStorage storage;
	private final CubeMetadataStorage metadataStorage;
	private final Aggregation aggregation;
	private final List<String> dimensions;
	private final List<String> measures;
	private final Class<?> accumulatorClass;
	private final Function<T, Comparable<?>> keyFunction;
	private final Aggregate aggregate;
	private final ResultCallback<List<AggregationChunk.NewChunk>> chunksCallback;
	private final int chunkSize;

	private final HashMap<Comparable<?>, Object> map = new HashMap<>();

	private final List<AggregationChunk.NewChunk> chunks = new ArrayList<>();
	private boolean saving;

	public AggregationGroupReducer(Eventloop eventloop, AggregationStorage storage, CubeMetadataStorage metadataStorage,
	                               Aggregation aggregation, List<String> dimensions, List<String> measures,
	                               Class<?> accumulatorClass,
	                               Function<T, Comparable<?>> keyFunction, Aggregate aggregate,
	                               ResultCallback<List<AggregationChunk.NewChunk>> chunksCallback, int chunkSize) {
		super(eventloop);
		this.storage = storage;
		this.metadataStorage = metadataStorage;
		this.aggregation = aggregation;
		this.dimensions = dimensions;
		this.measures = measures;
		this.accumulatorClass = accumulatorClass;
		this.keyFunction = keyFunction;
		this.aggregate = aggregate;
		this.chunksCallback = chunksCallback;
		this.chunkSize = chunkSize;
	}

	@Override
	public StreamDataReceiver<T> getDataReceiver() {
		return this;
	}

	@Override
	public void onData(T item) {
		Comparable<?> key = keyFunction.apply(item);
		Object accumulator = map.get(key);
		if (accumulator != null) {
			aggregate.accumulate(accumulator, item);
		} else {
			accumulator = aggregate.createAccumulator(item);
			map.put(key, accumulator);

			if (map.size() == chunkSize) {
				doNext();
			}
		}
	}

	private void doNext() {
		if (getUpstreamStatus() == StreamProducer.CLOSED) {
			return;
		}

		if (saving) {
			suspendUpstream();
			return;
		}

		if (getUpstreamStatus() == StreamProducer.END_OF_STREAM && map.isEmpty()) {
			chunksCallback.onResult(chunks);
			closeUpstream();
			logger.trace("{}: completed saving chunks {} for aggregation {}. Closing itself.", this, chunks, aggregation);
			return;
		}

		if (map.isEmpty()) {
			return;
		}

		saving = true;

		final List<Map.Entry<Comparable<?>, Object>> entryList = new ArrayList<>(map.entrySet());
		map.clear();

		Collections.sort(entryList, new Comparator<Map.Entry<Comparable<?>, Object>>() {
			@SuppressWarnings("unchecked")
			@Override
			public int compare(Map.Entry<Comparable<?>, Object> o1, Map.Entry<Comparable<?>, Object> o2) {
				Comparable<Object> key1 = (Comparable<Object>) o1.getKey();
				Comparable<Object> key2 = (Comparable<Object>) o2.getKey();
				return key1.compareTo(key2);
			}
		});

		metadataStorage.newChunkId(new ResultCallback<Long>() {
			@SuppressWarnings("unchecked")
			@Override
			public void onResult(Long newId) {
				AggregationChunk.NewChunk newChunk = new AggregationChunk.NewChunk(
						newId,
						aggregation.getId(), measures,
						PrimaryKey.ofObject(entryList.get(0).getValue(), dimensions),
						PrimaryKey.ofObject(entryList.get(entryList.size() - 1).getValue(), dimensions),
						entryList.size());
				chunks.add(newChunk);

				Iterable<Object> list = transform(entryList, new Function<Map.Entry<Comparable<?>, Object>, Object>() {
					@Override
					public Object apply(Map.Entry<Comparable<?>, Object> input) {
						return input.getValue();
					}
				});

				final StreamProducer<Object> producer = StreamProducers.ofIterable(eventloop, list);

				StreamConsumer consumer = storage.chunkWriter(aggregation.getId(), dimensions, measures, accumulatorClass, newId);

				producer.streamTo(consumer);

				producer.addCompletionCallback(new CompletionCallback() {
					@Override
					public void onComplete() {
						saving = false;
						metadataStorage.saveChunks(aggregation, chunks, new CompletionCallback() {
							@Override
							public void onComplete() {
								logger.trace("Saving chunks {} to metadata storage {} completed.", chunks, metadataStorage);
							}

							@Override
							public void onException(Exception exception) {
								logger.error("Saving chunks {} to metadata storage {} failed.", chunks, metadataStorage, exception);
							}
						});
						eventloop.post(new Runnable() {
							@Override
							public void run() {
								doNext();
							}
						});
					}

					@Override
					public void onException(Exception e) {
						logger.error("Saving chunks {} to aggregation storage {} failed.", chunks, storage, e);
					}
				});
				AggregationGroupReducer.this.resumeUpstream();
			}

			@Override
			public void onException(Exception exception) {
				logger.error("Failed to retrieve new chunk id from the metadata storage {}.", metadataStorage);
			}
		});
	}

	@Override
	public void onEndOfStream() {
		logger.trace("{}: upstream producer {} closed.", this, upstreamProducer);
		doNext();
	}

	@Override
	public void onError(Exception e) {
		logger.trace("{}: upstream producer {} exception.", this, upstreamProducer, e);
	}
}
