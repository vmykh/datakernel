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

import com.google.common.base.Predicate;
import io.datakernel.eventloop.NioEventloop;
import io.datakernel.stream.StreamConsumers;
import io.datakernel.stream.StreamProducer;
import io.datakernel.stream.StreamProducers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StreamFilterTest {
	@Test
	public void test1() throws Exception {
		NioEventloop eventloop = new NioEventloop();

		StreamProducer<Integer> source = StreamProducers.ofIterable(eventloop, asList(1, 2, 3));

		Predicate<Integer> predicate = new Predicate<Integer>() {
			@Override
			public boolean apply(Integer input) {
				return input % 2 == 1;
			}
		};
		StreamFilter<Integer> filter = new StreamFilter<>(eventloop, predicate);

		StreamConsumers.ToList<Integer> consumer = StreamConsumers.toListRandomlySuspending(eventloop);

		source.streamTo(filter);
		filter.streamTo(consumer);

		eventloop.run();
		assertEquals(asList(1, 3), consumer.getList());
		assertTrue(source.getStatus() == StreamProducer.CLOSED);
	}

	@Test
	public void testWithError() {
		NioEventloop eventloop = new NioEventloop();
		List<Integer> list = new ArrayList<>();

		StreamProducer<Integer> source = StreamProducers.ofIterable(eventloop, asList(1, 2, 3, 4, 5));

		Predicate<Integer> predicate = new Predicate<Integer>() {
			@Override
			public boolean apply(Integer input) {
				return input % 2 != 2;
			}
		};
		StreamFilter<Integer> streamFilter = new StreamFilter<>(eventloop, predicate);

		StreamConsumers.ToList<Integer> consumer1 = new StreamConsumers.ToList<Integer>(eventloop, list) {
			@Override
			public void onData(Integer item) {
				super.onData(item);
				if (item == 3) {
					onError(new Exception());
					return;
				}
				upstreamProducer.suspend();
				eventloop.post(new Runnable() {
					@Override
					public void run() {
						upstreamProducer.resume();
					}
				});
			}
		};

		source.streamTo(streamFilter);
		streamFilter.streamTo(consumer1);

		eventloop.run();

		assertEquals(asList(1, 2, 3), list);
		assertTrue(source.getStatus() == StreamProducer.CLOSED_WITH_ERROR);

	}

	@Test
	public void testEndOfStream() {
		NioEventloop eventloop = new NioEventloop();
		List<Integer> list = new ArrayList<>();

		StreamProducer<Integer> source = StreamProducers.ofIterable(eventloop, asList(1, 2, 3, 4, 5));

		Predicate<Integer> predicate = new Predicate<Integer>() {
			@Override
			public boolean apply(Integer input) {
				return input % 2 != 2;
			}
		};
		StreamFilter<Integer> streamFilter = new StreamFilter<>(eventloop, predicate);

		StreamConsumers.ToList<Integer> consumer1 = new StreamConsumers.ToList<Integer>(eventloop, list) {
			@Override
			public void onData(Integer item) {
				super.onData(item);
				if (item == 3) {
					onEndOfStream();
					return;
				}
				upstreamProducer.suspend();
				eventloop.post(new Runnable() {
					@Override
					public void run() {
						upstreamProducer.resume();
					}
				});
			}
		};

		source.streamTo(streamFilter);
		streamFilter.streamTo(consumer1);

		eventloop.run();

		assertEquals(asList(1, 2, 3), list);
		assertTrue(source.getStatus() == StreamProducer.CLOSED);

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testProducerDisconnectWithError() {
		NioEventloop eventloop = new NioEventloop();

		StreamProducer<Integer> source = StreamProducers.concat(eventloop,
				StreamProducers.ofIterable(eventloop, Arrays.asList(1, 2, 3)),
				StreamProducers.<Integer>closingWithError(eventloop, new Exception()),
				StreamProducers.ofValue(eventloop, 4),
				StreamProducers.ofValue(eventloop, 5)
		);

		Predicate<Integer> predicate = new Predicate<Integer>() {
			@Override
			public boolean apply(Integer input) {
				return input % 2 != 2;
			}
		};
		StreamFilter<Integer> streamFilter = new StreamFilter<>(eventloop, predicate);

		List<Integer> list = new ArrayList<>();
		StreamConsumers.ToList consumer = StreamConsumers.toListOneByOne(eventloop, list);

		source.streamTo(streamFilter);
		streamFilter.streamTo(consumer);

		eventloop.run();

		assertTrue(list.size() == 3);
		assertTrue(source.getStatus() == StreamProducer.CLOSED_WITH_ERROR);
	}
}
