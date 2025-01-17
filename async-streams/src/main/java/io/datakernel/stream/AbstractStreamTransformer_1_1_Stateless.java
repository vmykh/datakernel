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

import io.datakernel.eventloop.Eventloop;

/**
 * Represents  {@link AbstractStreamTransformer_1_1} with open consumer and producer statuses
 *
 * @param <I> type of input data for consumer
 * @param <O> type of output data of producer
 */
public abstract class AbstractStreamTransformer_1_1_Stateless<I, O> extends AbstractStreamTransformer_1_1<I, O> {

	protected AbstractStreamTransformer_1_1_Stateless(Eventloop eventloop) {
		super(eventloop);
	}

	@Override
	protected void onSuspended() {
		suspendUpstream();
	}

	@Override
	protected void onResumed() {
		resumeUpstream();
	}

	@Override
	public void onEndOfStream() {
		sendEndOfStream();
	}
}
