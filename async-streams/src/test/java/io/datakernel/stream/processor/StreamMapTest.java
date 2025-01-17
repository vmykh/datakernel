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

import io.datakernel.eventloop.NioEventloop;
import io.datakernel.stream.StreamConsumers;
import io.datakernel.stream.StreamProducer;
import io.datakernel.stream.StreamProducers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StreamMapTest {

	private static final StreamMap.MapperProjection<Integer, Integer> FUNCTION = new StreamMap.MapperProjection<Integer, Integer>() {
		@Override
		protected Integer apply(Integer input) {
			return input + 10;
		}
	};

	@Test
	public void test1() throws Exception {
		NioEventloop eventloop = new NioEventloop();

		StreamProducer<Integer> source = StreamProducers.ofIterable(eventloop, asList(1, 2, 3));
		StreamMap<Integer, Integer> projection = new StreamMap<>(eventloop, FUNCTION);
		StreamConsumers.ToList<Integer> consumer = StreamConsumers.toListRandomlySuspending(eventloop);

		source.streamTo(projection);
		projection.streamTo(consumer);

		eventloop.run();
		assertEquals(asList(11, 12, 13), consumer.getList());
		assertTrue(source.getStatus() == StreamProducer.CLOSED);
	}

	@Test
	public void testWithError() throws Exception {
		NioEventloop eventloop = new NioEventloop();
		List<Integer> list = new ArrayList<>();

		StreamProducer<Integer> source = StreamProducers.ofIterable(eventloop, asList(1, 2, 3));
		StreamMap<Integer, Integer> projection = new StreamMap<>(eventloop, FUNCTION);

		StreamConsumers.ToList<Integer> consumer = new StreamConsumers.ToList<Integer>(eventloop, list) {
			@Override
			public void onData(Integer item) {
				super.onData(item);
				if (item == 12) {
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

		source.streamTo(projection);
		projection.streamTo(consumer);

		eventloop.run();

		assertTrue(list.size() == 2);
		assertTrue(source.getStatus() == StreamProducer.CLOSED_WITH_ERROR);
	}

	@Test
	public void testEndofStream() throws Exception {
		NioEventloop eventloop = new NioEventloop();
		List<Integer> list = new ArrayList<>();

		StreamProducer<Integer> source = StreamProducers.ofIterable(eventloop, asList(1, 2, 3));
		StreamMap<Integer, Integer> projection = new StreamMap<>(eventloop, FUNCTION);

		StreamConsumers.ToList<Integer> consumer = new StreamConsumers.ToList<Integer>(eventloop, list) {
			@Override
			public void onData(Integer item) {
				super.onData(item);
				if (item == 12) {
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

		source.streamTo(projection);
		projection.streamTo(consumer);

		eventloop.run();

		assertTrue(list.size() == 2);
		assertTrue(source.getStatus() == StreamProducer.CLOSED);
	}

	@Test
	public void testProducerWithError() throws Exception {
		NioEventloop eventloop = new NioEventloop();

		StreamProducer<Integer> source = StreamProducers.concat(eventloop,
				StreamProducers.ofValue(eventloop, 1),
				StreamProducers.ofValue(eventloop, 2),
				StreamProducers.<Integer>closingWithError(eventloop, new Exception()),
				StreamProducers.ofValue(eventloop, 3));
		StreamMap<Integer, Integer> projection = new StreamMap<>(eventloop, FUNCTION);

		List<Integer> list = new ArrayList<>();
		StreamConsumers.ToList<Integer> consumer = StreamConsumers.toListOneByOne(eventloop, list);

		source.streamTo(projection);
		projection.streamTo(consumer);

		eventloop.run();
		assertTrue(list.size() == 2);
		assertTrue(source.getStatus() == StreamProducer.CLOSED_WITH_ERROR);
	}

}
