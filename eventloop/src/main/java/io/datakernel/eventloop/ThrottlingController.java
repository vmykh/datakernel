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

package io.datakernel.eventloop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.pow;

public final class ThrottlingController implements ThrottlingControllerMBean {
	private static int staticInstanceCounter = 0;

	private final Logger logger = LoggerFactory.getLogger(ThrottlingController.class.getName() + "." + staticInstanceCounter++);

	public static final int TARGET_TIME_MILLIS = 20;
	public static final int GC_TIME_MILLIS = 20;
	public static final int SMOOTHING_WINDOW = 10000;
	public static final double THROTTLING_DECREASE = 0.1;
	public static final double INITIAL_KEYS_PER_SECOND = 100;
	public static final double INITIAL_THROTTLING = 0.0;

	private static final Random random = new Random() {
		private long prev = System.nanoTime();

		@Override
		protected int next(int nbits) {
			long x = this.prev;
			x ^= (x << 21);
			x ^= (x >>> 35);
			x ^= (x << 4);
			this.prev = x;
			x &= ((1L << nbits) - 1);
			return (int) x;
		}
	};

	// settings
	private int targetTimeMillis;
	private int gcTimeMillis;
	private double throttlingDecrease;
	private int smoothingWindow;

	// intermediate counters for current round
	private int bufferedRequests;
	private int bufferedRequestsThrottled;

	// exponentially smoothed values
	private double smoothedThrottling;
	private double smoothedTimePerKeyMillis;

	// JMX
	private long infoTotalRequests;
	private long infoTotalRequestsThrottled;
	private long infoTotalTimeMillis;
	private long infoRounds;
	private long infoRoundsZeroThrottling;
	private long infoRoundsExceededTargetTime;
	private long infoRoundsGc;

	private float throttling;

	public static ThrottlingController createDefaultThrottlingController() {
		ThrottlingController throttlingController = new ThrottlingController();
		throttlingController.setTargetTimeMillis(TARGET_TIME_MILLIS);
		throttlingController.setGcTimeMillis(GC_TIME_MILLIS);
		throttlingController.setSmoothingWindow(SMOOTHING_WINDOW);
		throttlingController.setThrottlingDecrease(THROTTLING_DECREASE);
		throttlingController.init(INITIAL_KEYS_PER_SECOND, INITIAL_THROTTLING);
		return throttlingController;
	}

	public ThrottlingController() {
	}

	public void init(double initialKeysPerSecond, double initialThrottling) {
		this.smoothedTimePerKeyMillis = 1000.0 / initialKeysPerSecond;
		this.smoothedThrottling = initialThrottling;
	}

	public boolean isRequestThrottled() {
		bufferedRequests++;
		if (random.nextFloat() < throttling) {
			bufferedRequestsThrottled++;
			return true;
		}
		return false;
	}

	public void updateStats(int lastKeys, int lastTime) {
		if (lastTime < 0 || lastTime > 60000) {
			logger.warn("Invalid processing time: {}", lastTime);
			return;
		}

		int lastTimePredicted = (int) (lastKeys * getAvgTimePerKeyMillis());
		if (gcTimeMillis != 0.0 && lastTime > lastTimePredicted + gcTimeMillis) {
			logger.debug("GC detected {} ms, {} keys", lastTime, lastKeys);
			lastTime = lastTimePredicted + gcTimeMillis;
			infoRoundsGc++;
		}

		double weight = 1.0 - 1.0 / smoothingWindow;

		if (bufferedRequests != 0) {
			assert bufferedRequestsThrottled <= bufferedRequests;
			double value = (double) bufferedRequestsThrottled / bufferedRequests;
			smoothedThrottling = (smoothedThrottling - value) * pow(weight, bufferedRequests) + value;
			infoTotalRequests += bufferedRequests;
			infoTotalRequestsThrottled += bufferedRequestsThrottled;
			bufferedRequests = 0;
			bufferedRequestsThrottled = 0;
		}

		if (lastKeys != 0) {
			double value = (double) lastTime / lastKeys;
			smoothedTimePerKeyMillis = (smoothedTimePerKeyMillis - value) * pow(weight, lastKeys) + value;
		}

		infoTotalTimeMillis += lastTime;
	}

	public void calculateThrottling(int newKeys) {
		double predictedTime = newKeys * getAvgTimePerKeyMillis();

		double newThrottling = getAvgThrottling() - throttlingDecrease;
		if (newThrottling < 0)
			newThrottling = 0;
		if (predictedTime > targetTimeMillis) {
			double extraThrottling = 1.0 - targetTimeMillis / predictedTime;
			if (extraThrottling > newThrottling) {
				newThrottling = extraThrottling;
				infoRoundsExceededTargetTime++;
			}
		}

		if (newThrottling == 0)
			infoRoundsZeroThrottling++;
		infoRounds++;

		this.throttling = (float) newThrottling;
	}

	public double getAvgTimePerKeyMillis() {
		return smoothedTimePerKeyMillis;
	}

	@Override
	public double getAvgKeysPerSecond() {
		return 1000.0 / getAvgTimePerKeyMillis();
	}

	@Override
	public double getAvgThrottling() {
		return smoothedThrottling;
	}

	@Override
	public int getTargetTimeMillis() {
		return targetTimeMillis;
	}

	@Override
	public void setTargetTimeMillis(int targetTimeMillis) {
		checkArgument(targetTimeMillis > 0);
		this.targetTimeMillis = targetTimeMillis;
	}

	@Override
	public int getGcTimeMillis() {
		return gcTimeMillis;
	}

	@Override
	public void setGcTimeMillis(int gcTimeMillis) {
		checkArgument(gcTimeMillis > 0);
		this.gcTimeMillis = gcTimeMillis;
	}

	@Override
	public double getThrottlingDecrease() {
		return throttlingDecrease;
	}

	@Override
	public void setThrottlingDecrease(double throttlingDecrease) {
		checkArgument(throttlingDecrease >= 0.0 && throttlingDecrease <= 1.0);
		this.throttlingDecrease = throttlingDecrease;
	}

	@Override
	public int getSmoothingWindow() {
		return smoothingWindow;
	}

	@Override
	public void setSmoothingWindow(int smoothingWindow) {
		checkArgument(smoothingWindow > 0);
		this.smoothingWindow = smoothingWindow;
	}

	@Override
	public long getTotalRequests() {
		return infoTotalRequests;
	}

	@Override
	public long getTotalRequestsThrottled() {
		return infoTotalRequestsThrottled;
	}

	@Override
	public long getTotalProcessed() {
		return infoTotalRequests - infoTotalRequestsThrottled;
	}

	@Override
	public long getTotalTimeMillis() {
		return infoTotalTimeMillis;
	}

	@Override
	public long getRounds() {
		return infoRounds;
	}

	@Override
	public long getRoundsZeroThrottling() {
		return infoRoundsZeroThrottling;
	}

	@Override
	public long getRoundsExceededTargetTime() {
		return infoRoundsExceededTargetTime;
	}

	@Override
	public long getInfoRoundsGc() {
		return infoRoundsGc;
	}

	@Override
	public float getThrottling() {
		return throttling;
	}

	@Override
	public String getThrottlingStatus() {
		return toString();
	}

	@Override
	public void resetInfo() {
		infoTotalRequests = 0;
		infoTotalRequestsThrottled = 0;
		infoTotalTimeMillis = 0;
		infoRounds = 0;
		infoRoundsZeroThrottling = 0;
		infoRoundsExceededTargetTime = 0;
	}

	@Override
	public String toString() {
		return String.format("{throttling:%2d%% avgKps=%-4d avgThrottling=%2d%% requests=%-4d throttled=%-4d rounds=%-3d zero=%-3d >targetTime=%-3d}",
				(int) (throttling * 100),
				(int) getAvgKeysPerSecond(),
				(int) (getAvgThrottling() * 100),
				infoTotalRequests,
				infoTotalRequestsThrottled,
				infoRounds,
				infoRoundsZeroThrottling,
				infoRoundsExceededTargetTime);
	}
}
