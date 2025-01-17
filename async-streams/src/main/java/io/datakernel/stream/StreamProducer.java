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

import io.datakernel.annotation.Nullable;
import io.datakernel.async.CompletionCallback;

/**
 * It represents object for asynchronous sending streams of data.
 * Implementors of this interface are strongly encouraged to extend one of the abstract classes
 * in this package which implement this interface and make the threading and state management
 * easier.
 *
 * @param <T> type of output data
 */
public interface StreamProducer<T> {
	/**
	 * Changes consumer for this producer, removes itself from previous consumer and removes
	 * previous producer for new consumer. Begins to stream to consumer.
	 *
	 * @param downstreamConsumer consumer for streaming
	 */
	void streamTo(StreamConsumer<T> downstreamConsumer);

	/**
	 * This method is called if consumer was changed for changing consumer status of this producer
	 * and its dependencies
	 */
	void bindDataReceiver();

	/**
	 * Returns consumer for this producer
	 *
	 * @return consumer for this producer
	 */
	StreamConsumer<T> getDownstream();

	/**
	 * This method is called for stop streaming of this producer
	 */
	void suspend();

	/**
	 * This method is called for restore streaming of this producer
	 */
	void resume();

	/**
	 * This method is called for close streaming
	 */
	void close();

	/**
	 * This method is called for close with error
	 *
	 * @param e exception which was found
	 */
	void closeWithError(Exception e);

	byte READY = 0;
	byte SUSPENDED = 1;
	byte END_OF_STREAM = 2;
	byte CLOSED = 3;
	byte CLOSED_WITH_ERROR = 4;

	/**
	 * Returns current status of this producer
	 *
	 * @return current status of this producer
	 */
	byte getStatus();

	/**
	 * Returns exception which was found
	 *
	 * @return exception which was found
	 */
	@Nullable
	Exception getError();

	/**
	 * Adds new CompletionCallback which will be called when consumer closed or closed with error
	 *
	 * @param completionCallback new instance of CompletionCallback
	 */
	void addCompletionCallback(CompletionCallback completionCallback);

}
