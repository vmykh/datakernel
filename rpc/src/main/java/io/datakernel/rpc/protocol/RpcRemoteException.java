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

package io.datakernel.rpc.protocol;

import io.datakernel.serializer.annotations.Deserialize;
import io.datakernel.serializer.annotations.Serialize;
import io.datakernel.serializer.annotations.SerializeNullable;

public class RpcRemoteException extends Exception implements RpcMessage.RpcMessageData {
	private static final long serialVersionUID = 769022174067373741L;
	private final String causeMessage;
	private final String causeClassName;

	public RpcRemoteException(String message, Throwable cause) {
		super(message, cause);
		this.causeClassName = cause.getClass().getName();
		this.causeMessage = cause.getMessage();
	}

	public RpcRemoteException(Throwable cause) {
		this(cause.toString(), cause);
	}

	@SuppressWarnings("unused")
	public RpcRemoteException(String message) {
		super(message);
		this.causeClassName = null;
		this.causeMessage = null;
	}

	@SuppressWarnings("unused")
	public RpcRemoteException(@Deserialize(value = "message") String message, @Deserialize(value = "causeClassName") String causeClassName,
	                          @Deserialize(value = "causeMessage") String causeMessage) {
		super(message);
		this.causeClassName = causeClassName;
		this.causeMessage = causeMessage;
	}

	@Override
	public boolean isMandatory() {
		return true;
	}

	@Serialize(order = 1)
	@SerializeNullable
	public String getCauseMessage() {
		return causeMessage;
	}

	@Serialize(order = 0)
	@SerializeNullable
	public String getCauseClassName() {
		return causeClassName;
	}

	@Override
	@Serialize(order = 2)
	@SerializeNullable
	public String getMessage() {
		return super.getMessage();
	}
}
