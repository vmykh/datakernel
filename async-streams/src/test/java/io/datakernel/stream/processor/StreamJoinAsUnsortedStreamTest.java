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
import com.google.common.base.Objects;
import com.google.common.collect.Ordering;
import io.datakernel.eventloop.NioEventloop;
import io.datakernel.stream.StreamConsumers;
import io.datakernel.stream.StreamProducer;
import io.datakernel.stream.StreamProducers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Objects.equal;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class StreamJoinAsUnsortedStreamTest {
	private static final class DataItemMaster {
		int id;
		int detailId;
		String master;

		private DataItemMaster(int id, int detailId, String master) {
			this.id = id;
			this.detailId = detailId;
			this.master = master;
		}

		@Override
		public String toString() {
			return Objects.toStringHelper(this)
					.add("id", id)
					.add("detailId", detailId)
					.add("master", master)
					.toString();
		}
	}

	private static final class DataItemDetail {
		int id;
		String detail;

		private DataItemDetail(int id, String detail) {
			this.id = id;
			this.detail = detail;
		}

		@Override
		public String toString() {
			return Objects.toStringHelper(this)
					.add("id", id)
					.add("detail", detail)
					.toString();
		}
	}

	private static final class DataItemMasterDetail {
		int id;
		int detailId;
		String master;
		String detail;

		private DataItemMasterDetail(int id, int detailId, String master, String detail) {
			this.id = id;
			this.detailId = detailId;
			this.master = master;
			this.detail = detail;
		}

		@Override
		public boolean equals(Object o) {
			DataItemMasterDetail that = (DataItemMasterDetail) o;
			if (id != that.id) return false;
			if (detailId != that.detailId) return false;
			if (!equal(detail, that.detail)) return false;
			if (!equal(master, that.master)) return false;
			return true;
		}

		@Override
		public String toString() {
			return Objects.toStringHelper(this)
					.add("id", id)
					.add("detailId", detailId)
					.add("master", master)
					.add("detail", detail)
					.toString();
		}
	}

	@Test
	public void test1() throws Exception {
		NioEventloop eventloop = new NioEventloop();

		StreamProducer<DataItemMaster> source1 = StreamProducers.ofIterable(eventloop, asList(
				new DataItemMaster(10, 10, "masterA"),
				new DataItemMaster(20, 10, "masterB"),
				new DataItemMaster(25, 15, "masterB+"),
				new DataItemMaster(30, 20, "masterC"),
				new DataItemMaster(40, 20, "masterD")));

		StreamProducer<DataItemDetail> source2 = StreamProducers.ofIterable(eventloop, asList(
				new DataItemDetail(10, "detailX"),
				new DataItemDetail(20, "detailY")));

		StreamJoin<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail> streamJoin =
				new StreamJoin<>(eventloop, Ordering.<Integer>natural(),
						new Function<DataItemMaster, Integer>() {
							@Override
							public Integer apply(DataItemMaster input) {
								return input.detailId;
							}
						},
						new Function<DataItemDetail, Integer>() {
							@Override
							public Integer apply(DataItemDetail input) {
								return input.id;
							}
						},
						new StreamJoin.ValueJoiner<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail>() {
							@Override
							public DataItemMasterDetail doInnerJoin(Integer key, DataItemMaster left, DataItemDetail right) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, right.detail);
							}

							@Override
							public DataItemMasterDetail doLeftJoin(Integer key, DataItemMaster left) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, null);
							}
						}
				);

		StreamConsumers.ToList<DataItemMasterDetail> consumer = StreamConsumers.toListRandomlySuspending(eventloop);

		source1.streamTo(streamJoin.getLeft());
		source2.streamTo(streamJoin.getRight());

		streamJoin.streamTo(consumer);

		eventloop.run();

		List<DataItemMasterDetail> result = consumer.getList();
		assertArrayEquals(new DataItemMasterDetail[]{
						new DataItemMasterDetail(10, 10, "masterA", "detailX"),
						new DataItemMasterDetail(20, 10, "masterB", "detailX"),
						new DataItemMasterDetail(25, 15, "masterB+", null),
						new DataItemMasterDetail(30, 20, "masterC", "detailY"),
						new DataItemMasterDetail(40, 20, "masterD", "detailY")},
				result.toArray(new DataItemMasterDetail[result.size()]));
		assertTrue(source1.getStatus() == StreamProducer.CLOSED);
		assertTrue(source2.getStatus() == StreamProducer.CLOSED);
	}

	@Test
	public void testWithError() throws Exception {
		NioEventloop eventloop = new NioEventloop();
		List<DataItemMasterDetail> list = new ArrayList<>();

		StreamProducer<DataItemMaster> source1 = StreamProducers.ofIterable(eventloop, asList(
				new DataItemMaster(10, 10, "masterA"),
				new DataItemMaster(20, 10, "masterB"),
				new DataItemMaster(25, 15, "masterB+"),
				new DataItemMaster(30, 20, "masterC"),
				new DataItemMaster(40, 20, "masterD")));

		StreamProducer<DataItemDetail> source2 = StreamProducers.ofIterable(eventloop, asList(
				new DataItemDetail(10, "detailX"),
				new DataItemDetail(20, "detailY")));

		StreamJoin<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail> streamJoin =
				new StreamJoin<>(eventloop, Ordering.<Integer>natural(),
						new Function<DataItemMaster, Integer>() {
							@Override
							public Integer apply(DataItemMaster input) {
								return input.detailId;
							}
						},
						new Function<DataItemDetail, Integer>() {
							@Override
							public Integer apply(DataItemDetail input) {
								return input.id;
							}
						},
						new StreamJoin.ValueJoiner<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail>() {
							@Override
							public DataItemMasterDetail doInnerJoin(Integer key, DataItemMaster left, DataItemDetail right) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, right.detail);
							}

							@Override
							public DataItemMasterDetail doLeftJoin(Integer key, DataItemMaster left) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, null);
							}
						}
				);

		StreamConsumers.ToList<DataItemMasterDetail> consumer = new StreamConsumers.ToList<DataItemMasterDetail>(eventloop, list) {
			@Override
			public void onData(DataItemMasterDetail item) {
				super.onData(item);
				if (item.master.equals("masterB+")) {
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

		source1.streamTo(streamJoin.getLeft());
		source2.streamTo(streamJoin.getRight());

		streamJoin.streamTo(consumer);

		eventloop.run();
		assertTrue(list.size() == 3);
		assertTrue(source1.getStatus() == StreamProducer.CLOSED_WITH_ERROR);
		assertTrue(source2.getStatus() == StreamProducer.CLOSED_WITH_ERROR);
	}

	@Test
	public void testEndOfStream() throws Exception {
		NioEventloop eventloop = new NioEventloop();
		List<DataItemMasterDetail> list = new ArrayList<>();

		StreamProducer<DataItemMaster> source1 = StreamProducers.ofIterable(eventloop, asList(
				new DataItemMaster(10, 10, "masterA"),
				new DataItemMaster(20, 10, "masterB"),
				new DataItemMaster(25, 15, "masterB+"),
				new DataItemMaster(30, 20, "masterC"),
				new DataItemMaster(40, 20, "masterD")));

		StreamProducer<DataItemDetail> source2 = StreamProducers.ofIterable(eventloop, asList(
				new DataItemDetail(10, "detailX"),
				new DataItemDetail(20, "detailY")));

		StreamJoin<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail> streamJoin =
				new StreamJoin<>(eventloop, Ordering.<Integer>natural(),
						new Function<DataItemMaster, Integer>() {
							@Override
							public Integer apply(DataItemMaster input) {
								return input.detailId;
							}
						},
						new Function<DataItemDetail, Integer>() {
							@Override
							public Integer apply(DataItemDetail input) {
								return input.id;
							}
						},
						new StreamJoin.ValueJoiner<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail>() {
							@Override
							public DataItemMasterDetail doInnerJoin(Integer key, DataItemMaster left, DataItemDetail right) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, right.detail);
							}

							@Override
							public DataItemMasterDetail doLeftJoin(Integer key, DataItemMaster left) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, null);
							}
						}
				);

		StreamConsumers.ToList<DataItemMasterDetail> consumer = new StreamConsumers.ToList<DataItemMasterDetail>(eventloop, list) {
			@Override
			public void onData(DataItemMasterDetail item) {
				super.onData(item);
				if (item.master.equals("masterB+")) {
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

		source1.streamTo(streamJoin.getLeft());
		source2.streamTo(streamJoin.getRight());

		streamJoin.streamTo(consumer);

		eventloop.run();
		assertTrue(list.size() == 3);
		assertTrue(source1.getStatus() == StreamProducer.CLOSED);
		assertTrue(source2.getStatus() == StreamProducer.CLOSED);
	}

	@Test
	public void testProducerWithError() throws Exception {
		NioEventloop eventloop = new NioEventloop();
		StreamProducer<DataItemMaster> source1 = StreamProducers.concat(eventloop,
				StreamProducers.ofValue(eventloop, new DataItemMaster(10, 10, "masterA")),
				StreamProducers.<DataItemMaster>closingWithError(eventloop, new Exception()),
				StreamProducers.ofValue(eventloop, new DataItemMaster(20, 10, "masterB")),
				StreamProducers.ofValue(eventloop, new DataItemMaster(25, 15, "masterB+")),
				StreamProducers.ofValue(eventloop, new DataItemMaster(30, 20, "masterC")),
				StreamProducers.ofValue(eventloop, new DataItemMaster(40, 20, "masterD"))
		);

		StreamProducer<DataItemDetail> source2 = StreamProducers.concat(eventloop,
				StreamProducers.ofValue(eventloop, new DataItemDetail(10, "detailX")),
				StreamProducers.ofValue(eventloop, new DataItemDetail(20, "detailY"))
		);

		StreamJoin<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail> streamJoin =
				new StreamJoin<>(eventloop, Ordering.<Integer>natural(),
						new Function<DataItemMaster, Integer>() {
							@Override
							public Integer apply(DataItemMaster input) {
								return input.detailId;
							}
						},
						new Function<DataItemDetail, Integer>() {
							@Override
							public Integer apply(DataItemDetail input) {
								return input.id;
							}
						},
						new StreamJoin.ValueJoiner<Integer, DataItemMaster, DataItemDetail, DataItemMasterDetail>() {
							@Override
							public DataItemMasterDetail doInnerJoin(Integer key, DataItemMaster left, DataItemDetail right) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, right.detail);
							}

							@Override
							public DataItemMasterDetail doLeftJoin(Integer key, DataItemMaster left) {
								return new DataItemMasterDetail(left.id, left.detailId, left.master, null);
							}
						}
				);

		List<DataItemMasterDetail> list = new ArrayList<>();
		StreamConsumers.ToList<DataItemMasterDetail> consumer = StreamConsumers.toListOneByOne(eventloop, list);

		source1.streamTo(streamJoin.getLeft());
		source2.streamTo(streamJoin.getRight());

		streamJoin.streamTo(consumer);

		eventloop.run();
		assertTrue(list.size() == 1);
		assertTrue(source1.getStatus() == StreamProducer.CLOSED_WITH_ERROR);
		assertTrue(source2.getStatus() == StreamProducer.CLOSED_WITH_ERROR);
	}
}
