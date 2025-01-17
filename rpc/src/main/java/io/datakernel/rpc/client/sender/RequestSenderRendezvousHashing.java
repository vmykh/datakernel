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

package io.datakernel.rpc.client.sender;

import com.google.common.annotations.VisibleForTesting;
import io.datakernel.async.ResultCallback;
import io.datakernel.jmx.CompositeDataBuilder;
import io.datakernel.rpc.client.RpcClientConnection;
import io.datakernel.rpc.client.RpcClientConnectionPool;
import io.datakernel.rpc.hash.HashBucketAddresses;
import io.datakernel.rpc.hash.HashFunction;
import io.datakernel.rpc.protocol.RpcMessage;

import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.SimpleType;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

final class RequestSenderRendezvousHashing implements RequestSender {
	private static final RpcNoConnectionsException NO_AVAILABLE_CONNECTION = new RpcNoConnectionsException();
	private final RpcClientConnectionPool connections;
	private final HashFunction<RpcMessage.RpcMessageData> hashFunction;
	@VisibleForTesting
	final HashBucketAddresses hashBucket;

	// JMX
	private final long[] callCounters;

	public RequestSenderRendezvousHashing(RpcClientConnectionPool connections, HashFunction<RpcMessage.RpcMessageData> hashFunction) {
		this.connections = checkNotNull(connections);
		this.hashFunction = checkNotNull(hashFunction);
		this.hashBucket = new HashBucketAddresses(connections.addresses());

		this.callCounters = new long[connections.addresses().size()];
	}

	@Override
	public <T extends RpcMessage.RpcMessageData> void sendRequest(RpcMessage.RpcMessageData request, int timeout, final ResultCallback<T> callback) {
		checkNotNull(callback);
		RpcClientConnection connection = getConnection(request);
		if (connection == null) {
			callback.onException(NO_AVAILABLE_CONNECTION);
			return;
		}
		connection.callMethod(request, timeout, new ResultCallback<T>() {
			@Override
			public void onException(Exception exception) {
				callback.onException(exception);
			}

			@Override
			public void onResult(T result) {
				callback.onResult(result);
			}

		});
	}

	private RpcClientConnection getConnection(RpcMessage.RpcMessageData request) {
		int hashRequest = hashFunction.hashCode(request);
		int serverId = hashBucket.getAddressId(hashRequest);
		if (serverId == -1)
			return null;
		++callCounters[serverId];
		return connections.get(connections.addresses().get(serverId));
	}

	@Override
	public void onConnectionsUpdated() {
		hashBucket.updateBucket(connections.activeAddresses());
	}

	// JMX
	@Override
	public void resetStats() {
		for (int i = 0; i < callCounters.length; i++) {
			callCounters[i] = 0;
		}
	}

	@Override
	public CompositeData getRequestSenderInfo() throws OpenDataException {
		List<String> res = new ArrayList<>();
		res.add("address;calls");
		for (int i = 0; i < connections.addresses().size(); i++) {
			res.add(connections.addresses().get(i) + ";" + callCounters[i]);
		}
		return CompositeDataBuilder.builder(RequestSenderRendezvousHashing.class.getSimpleName())
				.add("connectionDispatcher", SimpleType.STRING, RequestSenderRendezvousHashing.class.getSimpleName())
				.add("callsPerAddress", new ArrayType<>(1, SimpleType.STRING), res.toArray(new String[res.size()]))
				.build();
	}

}
