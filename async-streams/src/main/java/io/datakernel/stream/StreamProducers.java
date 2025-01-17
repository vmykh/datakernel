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

package io.datakernel.stream;

import io.datakernel.async.*;
import io.datakernel.eventloop.Eventloop;

import java.util.Arrays;
import java.util.Iterator;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.datakernel.async.AsyncIterators.asyncIteratorOfIterator;

public class StreamProducers {
	private StreamProducers() {
	}

	/**
	 * Returns producer which doing nothing - not sending any data and not closing itself.
	 *
	 * @param eventloop event loop in which will run it
	 */
	public static <T> StreamProducer<T> idle(Eventloop eventloop) {
		return new Idle<>(eventloop);
	}

	/**
	 * Returns producer which closes itself
	 *
	 * @param eventloop event loop in which will run it
	 * @param <T>       type of item for send
	 */
	public static <T> StreamProducer<T> closing(Eventloop eventloop) {
		return new EndOfStream<>(eventloop);
	}

	public static <T> StreamProducer<T> closingWithError(Eventloop eventloop, Exception e) {
		return new ClosingWithError<>(eventloop, e);
	}

	/**
	 * Creates producer which sends value and closes itself
	 *
	 * @param eventloop event loop in which will run it
	 * @param value     value for sending
	 * @param <T>       type of value
	 */
	public static <T> StreamProducer<T> ofValue(Eventloop eventloop, final T value) {
		return new OfValue<>(eventloop, value);
	}

	/**
	 * Creates producer which sends value and closes itself
	 *
	 * @param eventloop event loop in which will run it
	 * @param value     value for sending
	 * @param close     if producer is closed
	 * @param <T>       type of value
	 */
	public static <T> StreamProducer<T> ofValue(Eventloop eventloop, final T value, boolean close) {
		return new OfValue<>(eventloop, value, close);
	}

	/**
	 * Returns new {@link OfIterator} which sends items from iterator
	 *
	 * @param eventloop event loop in which will run it
	 * @param iterator  iterator with items for sending
	 * @param <T>       type of item
	 */
	public static <T> StreamProducer<T> ofIterator(Eventloop eventloop, Iterator<T> iterator) {
		return new OfIterator<>(eventloop, iterator);
	}

	/**
	 * Returns new {@link OfIterator} which sends items from {@code iterable}
	 *
	 * @param eventloop event loop in which will run it
	 * @param iterable  iterable with items for sending
	 * @param <T>       type of item
	 */
	public static <T> StreamProducer<T> ofIterable(Eventloop eventloop, Iterable<T> iterable) {
		return new OfIterator<>(eventloop, iterable.iterator());
	}

	/**
	 * Represents asynchronously resolving producer.
	 *
	 * @param eventloop      event loop in which will run it
	 * @param producerGetter getter with producer
	 * @param <T>            type of output data
	 */
	public static <T> StreamProducer<T> asynchronouslyResolving(final Eventloop eventloop, final AsyncGetter<StreamProducer<T>> producerGetter) {
		final StreamForwarder<T> forwarder = new StreamForwarder<>(eventloop);
		eventloop.post(new Runnable() {
			@Override
			public void run() {
				producerGetter.get(new ResultCallback<StreamProducer<T>>() {
					@Override
					public void onResult(StreamProducer<T> actualProducer) {
						actualProducer.streamTo(forwarder);
					}

					@Override
					public void onException(Exception exception) {
						new ClosingWithError<T>(eventloop, exception).streamTo(forwarder);
					}
				});
			}
		});
		return forwarder;
	}

	/**
	 * Returns {@link StreamProducerConcat} with producers from asyncIterator  which will stream to this
	 *
	 * @param eventloop     event loop in which will run it
	 * @param asyncIterator iterator with producers
	 * @param <T>           type of output data
	 */
	public static <T> StreamProducer<T> concat(Eventloop eventloop, AsyncIterator<StreamProducer<T>> asyncIterator) {
		return new StreamProducerConcat<>(eventloop, asyncIterator);
	}

	/**
	 * Returns  {@link StreamProducerConcat} with producers from AsyncIterable  which will stream to this
	 *
	 * @param eventloop     event loop in which will run it
	 * @param asyncIterator iterable with producers
	 * @param <T>           type of output data
	 */
	public static <T> StreamProducer<T> concat(Eventloop eventloop, AsyncIterable<StreamProducer<T>> asyncIterator) {
		return concat(eventloop, asyncIterator.asyncIterator());
	}

	/**
	 * Returns  {@link StreamProducerConcat} with producers from Iterator  which will stream to this
	 *
	 * @param eventloop event loop in which will run it
	 * @param iterator  iterator with producers
	 * @param <T>       type of output data
	 */
	public static <T> StreamProducer<T> concat(Eventloop eventloop, Iterator<StreamProducer<T>> iterator) {
		return concat(eventloop, asyncIteratorOfIterator(iterator));
	}

	/**
	 * Returns  {@link StreamProducerConcat} with producers from Iterable which will stream to this
	 *
	 * @param eventloop event loop in which will run it
	 * @param iterable  iterator with producers
	 * @param <T>       type of output data
	 */
	public static <T> StreamProducer<T> concat(Eventloop eventloop, Iterable<StreamProducer<T>> iterable) {
		return concat(eventloop, iterable.iterator());
	}

	@SafeVarargs
	public static <T> StreamProducer<T> concat(Eventloop eventloop, StreamProducer<T>... producers) {
		return concat(eventloop, Arrays.asList(producers));
	}

	/**
	 * Represent a {@link AbstractStreamProducer} which once sends to consumer end of stream.
	 *
	 * @param <T>
	 */
	public static class EndOfStream<T> extends AbstractStreamProducer<T> {
		public EndOfStream(Eventloop eventloop) {
			super(eventloop);
		}

		@Override
		protected void onProducerStarted() {
			sendEndOfStream();
		}
	}

	/**
	 * Represent producer which sends specified exception to consumer.
	 *
	 * @param <T>
	 */
	public static class ClosingWithError<T> extends AbstractStreamProducer<T> {
		private final Exception exception;

		public ClosingWithError(Eventloop eventloop, Exception exception) {
			super(eventloop);
			this.exception = exception;
		}

		@Override
		protected void onProducerStarted() {
			closeWithError(exception);
		}
	}

	public static class Idle<T> extends AbstractStreamProducer<T> {
		public Idle(Eventloop eventloop) {
			super(eventloop);
		}
	}

	/**
	 * Represents a {@link AbstractStreamProducer} which will send all values from iterator.
	 *
	 * @param <T> type of output data
	 */
	public static class OfIterator<T> extends AbstractStreamProducer<T> {
		private final Iterator<T> iterator;
		private boolean sendEndOfStream = true;

		/**
		 * Creates a new instance of  StreamProducerOfIterator
		 *
		 * @param eventloop event loop where producer will run
		 * @param iterator  iterator with object which need to send
		 */
		public OfIterator(Eventloop eventloop, Iterator<T> iterator) {
			this(eventloop, iterator, true);
		}

		public OfIterator(Eventloop eventloop, Iterator<T> iterator, boolean sendEndOfStream) {
			super(eventloop);
			this.iterator = checkNotNull(iterator);
			this.sendEndOfStream = sendEndOfStream;
		}

		@Override
		protected void doProduce() {
			for (; ; ) {
				if (!iterator.hasNext())
					break;
				if (status != READY)
					return;
				T item = iterator.next();
				send(item);
			}
			if (sendEndOfStream)
				sendEndOfStream();
		}

		@Override
		protected void onProducerStarted() {
			produce();
		}

		@Override
		protected void onResumed() {
			resumeProduce();
		}
	}

	/**
	 * It is {@link AbstractStreamProducer} which sends specified single value to its consumer, followed by end-of-stream
	 *
	 * @param <T> type of value for send
	 */
	public static class OfValue<T> extends AbstractStreamProducer<T> {
		private final T value;
		private final boolean sendEndOfStream;

		/**
		 * Creates producer which sends value and closes itself
		 *
		 * @param eventloop event loop  in which this producer will run
		 * @param value     value for sending
		 */
		public OfValue(Eventloop eventloop, T value) {
			this(eventloop, value, true);
		}

		/**
		 * Creates producer which sends value and optionally closes itself
		 *
		 * @param eventloop       event loop  in which this producer will run
		 * @param value           value for sending
		 * @param sendEndOfStream if producer is closed
		 */
		public OfValue(Eventloop eventloop, T value, boolean sendEndOfStream) {
			super(eventloop);
			this.value = value;
			this.sendEndOfStream = sendEndOfStream;
		}

		@Override
		protected void onProducerStarted() {
			send(value);
			if (sendEndOfStream)
				sendEndOfStream();
		}
	}

	/**
	 * Represents {@link AbstractStreamTransformer_1_1}, which created with iterator with {@link AbstractStreamProducer}
	 * which will stream to this
	 *
	 * @param <T> type of received data
	 */
	public static class StreamProducerConcat<T> extends StreamProducerDecorator<T> {
		private final AsyncIterator<StreamProducer<T>> iterator;
		private final StreamProducerSwitcher<T> switcher;

		public StreamProducerConcat(Eventloop eventloop, AsyncIterator<StreamProducer<T>> iterator) {
			super(eventloop);
			this.iterator = checkNotNull(iterator);
			this.switcher = new StreamProducerSwitcher<>(eventloop);
			decorate(switcher);
		}

		/**
		 * This method is called if consumer was changed for changing consumer status. It begins streaming
		 * from producers from iterator
		 */
		@Override
		protected void onProducerStarted() {
			doNext();
		}

		private void doNext() {
			eventloop.post(new Runnable() {
				@Override
				public void run() {
					iterator.next(new IteratorCallback<StreamProducer<T>>() {
						@Override
						public void onNext(StreamProducer<T> actualProducer) {
							switcher.switchProducerTo(new StreamProducerDecorator<T>(eventloop, actualProducer) {
								@Override
								public void onEndOfStream() {
									doNext();
								}
							});
						}

						@Override
						public void onEnd() {
							switcher.switchProducerTo(new EndOfStream<T>(eventloop));
						}

						@Override
						public void onException(Exception e) {
							switcher.switchProducerTo(new ClosingWithError<T>(eventloop, e));
						}
					});
				}
			});
		}

	}
}
