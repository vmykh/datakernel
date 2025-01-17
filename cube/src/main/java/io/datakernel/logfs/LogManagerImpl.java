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

package io.datakernel.logfs;

import io.datakernel.async.ResultCallback;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.serializer.BufferSerializer;
import io.datakernel.stream.StreamConsumer;
import io.datakernel.stream.StreamProducer;

public final class LogManagerImpl<T> implements LogManager<T> {
	private final Eventloop eventloop;
	private final LogFileSystem fileSystem;
	private final BufferSerializer<T> serializer;

	public LogManagerImpl(Eventloop eventloop, LogFileSystem fileSystem, BufferSerializer<T> serializer) {
		this.eventloop = eventloop;
		this.fileSystem = fileSystem;
		this.serializer = serializer;
	}

	@Override
	public StreamConsumer<T> consumer(String streamId) {
		return new LogStreamConsumer<>(eventloop, fileSystem, serializer, streamId);
	}

	@Override
	public StreamProducer<T> producer(String logPartition, LogFile logFile, long position,
	                                  ResultCallback<LogPosition> positionCallback) {
		return new LogStreamProducer<>(eventloop, fileSystem, serializer, logPartition, new LogPosition(logFile, position), positionCallback);
	}

}
