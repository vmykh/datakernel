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

package io.datakernel.net;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import static com.google.common.base.Preconditions.checkState;
import static java.net.StandardSocketOptions.*;

/**
 * This class used to change settings for socket. It will be applying with creating new socket
 */
public class SocketSettings {
	private static final SocketSettings DEFAULT_SOCKET_SETTINGS = new SocketSettings();

	public static SocketSettings defaultSocketSettings() {
		return DEFAULT_SOCKET_SETTINGS;
	}

	protected static final int DEF_INT = -1;
	protected static final byte DEF_BOOL = -1;
	protected static final byte TRUE = 1;
	protected static final byte FALSE = 0;

	private final int sendBufferSize;
	private final int receiveBufferSize;
	private final byte keepAlive;
	private final byte reuseAddress;
	private final byte tcpNoDelay;

	protected SocketSettings(int sendBufferSize, int receiveBufferSize, byte keepAlive, byte reuseAddress, byte tcpNoDelay) {
		this.sendBufferSize = sendBufferSize;
		this.receiveBufferSize = receiveBufferSize;
		this.keepAlive = keepAlive;
		this.reuseAddress = reuseAddress;
		this.tcpNoDelay = tcpNoDelay;
	}

	public SocketSettings() {
		this(DEF_INT, DEF_INT, DEF_BOOL, DEF_BOOL, DEF_BOOL);
	}

	public SocketSettings sendBufferSize(int sendBufferSize) {
		return new SocketSettings(sendBufferSize, receiveBufferSize, keepAlive, reuseAddress, tcpNoDelay);
	}

	public SocketSettings receiveBufferSize(int receiveBufferSize) {
		return new SocketSettings(sendBufferSize, receiveBufferSize, keepAlive, reuseAddress, tcpNoDelay);
	}

	public SocketSettings keepAlive(boolean keepAlive) {
		return new SocketSettings(sendBufferSize, receiveBufferSize, keepAlive ? TRUE : FALSE, reuseAddress, tcpNoDelay);
	}

	public SocketSettings reuseAddress(boolean reuseAddress) {
		return new SocketSettings(sendBufferSize, receiveBufferSize, keepAlive, reuseAddress ? TRUE : FALSE, tcpNoDelay);
	}

	public SocketSettings tcpNoDelay(boolean tcpNoDelay) {
		return new SocketSettings(sendBufferSize, receiveBufferSize, keepAlive, reuseAddress, tcpNoDelay ? TRUE : FALSE);
	}

	public void applySettings(SocketChannel channel) throws IOException {
		if (sendBufferSize != DEF_INT) {
			channel.setOption(SO_SNDBUF, sendBufferSize);
		}
		if (receiveBufferSize != DEF_INT) {
			channel.setOption(SO_RCVBUF, receiveBufferSize);
		}
		if (keepAlive != DEF_BOOL) {
			channel.setOption(SO_KEEPALIVE, keepAlive != FALSE);
		}
		if (reuseAddress != DEF_BOOL) {
			channel.setOption(SO_REUSEADDR, reuseAddress != FALSE);
		}
		if (tcpNoDelay != DEF_BOOL) {
			channel.setOption(TCP_NODELAY, tcpNoDelay != FALSE);
		}
	}

	public boolean hasSendBufferSize() {
		return sendBufferSize != DEF_INT;
	}

	public int getSendBufferSize() {
		checkState(hasSendBufferSize());
		return sendBufferSize;
	}

	public boolean hasReceiveBufferSize() {
		return receiveBufferSize != DEF_INT;
	}

	public int getReceiveBufferSize() {
		checkState(hasReceiveBufferSize());
		return receiveBufferSize;
	}

	public boolean hasKeepAlive() {
		return keepAlive != DEF_BOOL;
	}

	public boolean getKeepAlive() {
		checkState(hasKeepAlive());
		return keepAlive != FALSE;
	}

	public boolean hasReuseAddress() {
		return reuseAddress != DEF_BOOL;
	}

	public boolean getReuseAddress() {
		checkState(hasReuseAddress());
		return reuseAddress != FALSE;
	}

	public boolean hasTcpNoDelay() {
		return tcpNoDelay != DEF_BOOL;
	}

	public boolean getTcpNoDelay() {
		checkState(hasTcpNoDelay());
		return tcpNoDelay != FALSE;
	}

}
