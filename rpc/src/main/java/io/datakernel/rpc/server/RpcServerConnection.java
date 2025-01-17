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

package io.datakernel.rpc.server;

import io.datakernel.async.ResultCallback;
import io.datakernel.eventloop.NioEventloop;
import io.datakernel.eventloop.SocketConnection;
import io.datakernel.jmx.LastExceptionCounter;
import io.datakernel.jmx.StatsCounter;
import io.datakernel.rpc.protocol.*;
import io.datakernel.time.CurrentTimeProvider;
import io.datakernel.time.CurrentTimeProviderSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.OpenDataException;
import java.nio.channels.SocketChannel;

public final class RpcServerConnection implements RpcConnection, RpcServerConnectionMBean {

	public interface StatusListener {
		void onOpen(RpcServerConnection connection);

		void onClosed();
	}

	private static final Logger logger = LoggerFactory.getLogger(RpcServerConnection.class);
	private final NioEventloop eventloop;
	private final RpcProtocol protocol;
	private final RequestHandlers handlers;
	private final StatusListener statusListener;

	// JMX
	private final LastExceptionCounter lastRemoteException = new LastExceptionCounter("RemoteException");
	private final LastExceptionCounter lastInternalException = new LastExceptionCounter("InternalException");
	private final CurrentTimeProvider statsTimeProvider = CurrentTimeProviderSystem.instance();
	private final StatsCounter timeExecution = new StatsCounter();
	private int successfulResponses = 0;
	private int errorResponses = 0;
	private boolean monitoring;

	public RpcServerConnection(NioEventloop eventloop, SocketChannel socketChannel, RpcMessageSerializer serializer, RequestHandlers handlers,
	                           RpcProtocolFactory protocolFactory, StatusListener statusListener) {
		this.eventloop = eventloop;
		this.protocol = protocolFactory.create(this, socketChannel, serializer, true);
		this.handlers = handlers;
		this.statusListener = statusListener;
	}

	@Override
	public void onReceiveMessage(final RpcMessage message) {
		final int cookie = message.getCookie();
		final long startTime = monitoring ? statsTimeProvider.currentTimeMillis() : 0;

		handlers.apply(message.getData(), new ResultCallback<RpcMessage.RpcMessageData>() {
			@Override
			public void onResult(RpcMessage.RpcMessageData result) {
				updateProcessTime();
				try {
					protocol.sendMessage(new RpcMessage(cookie, result));
				} catch (Exception e) {
					onException(e);
					return;
				}
				successfulResponses++;
			}

			@Override
			public void onException(Exception exception) {
				updateProcessTime();
				lastRemoteException.update(exception, message.getData(), eventloop.currentTimeMillis());
				sendError(cookie, exception);
			}

			private void updateProcessTime() {
				if (startTime == 0)
					return;
				int value = (int) (statsTimeProvider.currentTimeMillis() - startTime);
				timeExecution.add(value);
			}
		});
	}

	private void sendError(int cookie, Exception error) {
		try {
			protocol.sendMessage(new RpcMessage(cookie, new RpcRemoteException(error)));
			logger.error(lastRemoteException.getMarker(), "Exception while process request ID {}", cookie, error);
			errorResponses++;
		} catch (Exception exception) {
			lastInternalException.update(exception, error, eventloop.currentTimeMillis());
			throw new RuntimeException(exception);
		}
	}

	@Override
	public void ready() {
		statusListener.onOpen(this);
	}

	@Override
	public void onClosed() {
		statusListener.onClosed();
	}

	public void close() {
		protocol.close();
	}

	@Override
	public NioEventloop getEventloop() {
		return eventloop;
	}

	public SocketConnection getSocketConnection() {
		return protocol.getSocketConnection();
	}

	// JMX
	@Override
	public void startMonitoring() {
		monitoring = true;
		protocol.startMonitoring();
	}

	@Override
	public void stopMonitoring() {
		monitoring = false;
		protocol.stopMonitoring();
	}

	@Override
	public boolean isMonitoring() {
		return monitoring;
	}

	@Override
	public void reset() {
		lastRemoteException.reset();
		lastInternalException.reset();
		successfulResponses = 0;
		errorResponses = 0;
		timeExecution.reset();
		protocol.reset();
	}

	@Override
	public CompositeData getConnectionDetails() throws OpenDataException {
		return protocol.getConnectionDetails();
	}

	@Override
	public int getSuccessfulResponses() {
		return successfulResponses;
	}

	@Override
	public int getErrorResponses() {
		return errorResponses;
	}

	@Override
	public String getTimeExecutionMillis() {
		return timeExecution.toString();
	}

	@Override
	public CompositeData getLastResponseException() {
		return lastRemoteException.compositeData();
	}

	@Override
	public CompositeData getLastInternalException() {
		return lastInternalException.compositeData();
	}
}
