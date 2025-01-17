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

package io.datakernel.hashfs;

import io.datakernel.async.ResultCallback;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.stream.StreamConsumer;
import io.datakernel.stream.StreamProducer;

public interface HashFs {

	StreamConsumer<ByteBuf> upload(String filename);

	StreamProducer<ByteBuf> download(String filename);

	/**
	 * Delete file by path <b>remotePath</b> from FS.
	 * <p>In <b>callback</b> return true if file successfully deleted and false otherwise.
	 */
	void deleteFile(final String filename, final ResultCallback<Boolean> callback);

}
