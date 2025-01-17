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

package io.datakernel.rpc.client;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.OpenDataException;

public interface RpcClientConnectionPoolMBean {
	void startMonitoring();

	void stopMonitoring();

	boolean isMonitoring();

	void resetStats();

	int getConnectionsCount();

	CompositeData[] getConnections() throws OpenDataException;

	long getTotalSuccessfulRequests();

	long getTotalPendingRequests();

	long getTotalRejectedRequests();

	long getTotalFailedRequests();

	long getTotalExpiredRequests();
}
