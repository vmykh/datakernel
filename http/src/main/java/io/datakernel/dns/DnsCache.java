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

package io.datakernel.dns;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import io.datakernel.async.ResultCallback;
import io.datakernel.eventloop.NioEventloop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.datakernel.dns.DnsMessage.AAAA_RECORD_TYPE;
import static io.datakernel.dns.DnsMessage.A_RECORD_TYPE;

/**
 * Represents a cache for storing resolved domains during its time to live.
 */
public final class DnsCache {
	private static final Logger logger = LoggerFactory.getLogger(DnsCache.class);

	private final Map<String, CachedDnsLookupResult> cache = new ConcurrentHashMap<>();
	private final Multimap<Long, String> expirations = HashMultimap.create();
	private long lastCleanupSecond;

	private final long errorCacheExpirationMillis;
	private final long hardExpirationDeltaMillis;
	private final NioEventloop eventloop;

	/**
	 * Enum with freshness cache's entry.
	 * <ul>
	 * <li>FRESH - while time to live of this entry has not passed, empty is considered resolved
	 * <li> SOFT_TTL_EXPIRED - while hard time expiration has not passed, empty is considered resolved, but needs refreshing
	 * <li> HARD_TTL_EXPIRED - while hard time expiration has passed, empty is considered not resolved
	 * </ul>
	 */
	public enum DnsCacheEntryFreshness {
		FRESH,
		SOFT_TTL_EXPIRED,
		HARD_TTL_EXPIRED,
	}

	public enum DnsCacheQueryResult {
		RESOLVED,
		RESOLVED_NEEDS_REFRESHING,
		NOT_RESOLVED
	}

	/**
	 * Creates a new DNS cache.
	 *
	 * @param eventloop                  eventloop in which its task will be ran
	 * @param errorCacheExpirationMillis expiration time for errors without time to live
	 * @param hardExpirationDeltaMillis  delta between time at which entry is considered resolved, but needs
	 *                                   refreshing and time at which entry is considered not resolved
	 */
	public DnsCache(NioEventloop eventloop, long errorCacheExpirationMillis, long hardExpirationDeltaMillis) {
		this.errorCacheExpirationMillis = errorCacheExpirationMillis;
		this.hardExpirationDeltaMillis = hardExpirationDeltaMillis;
		this.eventloop = eventloop;
		this.lastCleanupSecond = getCurrentSecond();
	}

	private boolean isRequestedType(CachedDnsLookupResult cachedResult, boolean requestedIpv6) {
		Short cachedResultType = cachedResult.getType();

		if (cachedResultType == A_RECORD_TYPE & !requestedIpv6)
			return true;

		if (cachedResultType == AAAA_RECORD_TYPE & requestedIpv6)
			return true;

		else return false;
	}

	/**
	 * Tries to gets status of the entry for some domain name from the cache.
	 *
	 * @param domainName domain name for finding entry
	 * @param ipv6       type of result, if true - IPv6, false - IPv4
	 * @param callback   callback with which it will handle result
	 * @return DnsCacheQueryResult for this domain name
	 */
	public DnsCacheQueryResult tryToResolve(String domainName, boolean ipv6, ResultCallback<InetAddress[]> callback) {
		CachedDnsLookupResult cachedResult = cache.get(domainName);

		if (cachedResult == null) {
			if (logger.isDebugEnabled())
				logger.debug("Cache miss for host: {}", domainName);
			return DnsCacheQueryResult.NOT_RESOLVED;
		}

		if (cachedResult.isSuccessful() && !isRequestedType(cachedResult, ipv6)) {
			if (logger.isDebugEnabled())
				logger.debug("Cache miss for host: {}", domainName);
			return DnsCacheQueryResult.NOT_RESOLVED;
		}

		DnsCacheEntryFreshness freshness = getResultFreshness(cachedResult);

		switch (freshness) {
			case HARD_TTL_EXPIRED: {
				if (logger.isDebugEnabled())
					logger.debug("Hard TTL expired for host: {}", domainName);
				return DnsCacheQueryResult.NOT_RESOLVED;
			}

			case SOFT_TTL_EXPIRED: {
				if (logger.isDebugEnabled())
					logger.debug("Soft TTL expired for host: {}", domainName);
				returnResultThroughCallback(domainName, cachedResult, callback);
				return DnsCacheQueryResult.RESOLVED_NEEDS_REFRESHING;
			}

			default: {
				returnResultThroughCallback(domainName, cachedResult, callback);
				return DnsCacheQueryResult.RESOLVED;
			}
		}
	}

	private void returnResultThroughCallback(String domainName, CachedDnsLookupResult result, ResultCallback<InetAddress[]> callback) {
		if (result.isSuccessful()) {
			InetAddress[] ipsFromCache = result.getIps();
			callback.onResult(ipsFromCache);
			if (logger.isDebugEnabled())
				logger.debug("Cache hit for host: {}", domainName);
		} else {
			DnsException exception = result.getException();
			callback.onException(exception);
			if (logger.isDebugEnabled())
				logger.debug("Error cache hit for host: {}", domainName);
		}
	}

	private DnsCacheEntryFreshness getResultFreshness(CachedDnsLookupResult result) {
		Long softExpirationSecond = result.getExpirationSecond();
		long hardExpirationSecond = softExpirationSecond + hardExpirationDeltaMillis / 1000;
		long currentSecond = getCurrentSecond();

		if (currentSecond >= hardExpirationSecond)
			return DnsCacheEntryFreshness.HARD_TTL_EXPIRED;
		else if (currentSecond >= softExpirationSecond)
			return DnsCacheEntryFreshness.SOFT_TTL_EXPIRED;
		else
			return DnsCacheEntryFreshness.FRESH;
	}

	/**
	 * Adds DnsQueryResult to this cache
	 *
	 * @param result result to add
	 */
	public void add(DnsQueryResult result) {
		if (result.getMinTtl() == 0)
			return;
		long expirationSecond = result.getMinTtl() + getCurrentSecond();
		String domainName = result.getDomainName();
		cache.put(domainName, CachedDnsLookupResult.fromQueryWithExpiration(result, expirationSecond));
		expirations.put(expirationSecond + hardExpirationDeltaMillis / 1000, domainName);
	}

	/**
	 * Adds DnsException to this cache
	 *
	 * @param exception exception to add
	 */
	public void add(DnsException exception) {
		long expirationSecond = errorCacheExpirationMillis / 1000 + getCurrentSecond();
		String domainName = exception.getDomainName();
		cache.put(domainName, CachedDnsLookupResult.fromExceptionWithExpiration(exception, expirationSecond));
		expirations.put(expirationSecond, domainName);
	}

	public void performCleanup() {
		long callSecond = getCurrentSecond();

		if (callSecond > lastCleanupSecond) {
			clearCache(callSecond, lastCleanupSecond);
			lastCleanupSecond = callSecond;
		}
	}

	private void clearCache(long callSecond, long lastCleanupSecond) {
		for (long i = lastCleanupSecond; i <= callSecond; ++i) {
			Collection<String> domainNames = expirations.removeAll(i);

			if (domainNames != null) {
				for (String domainName : domainNames) {
					CachedDnsLookupResult cachedResult = cache.get(domainName);
					if (cachedResult != null && getResultFreshness(cachedResult) == DnsCacheEntryFreshness.HARD_TTL_EXPIRED) {
						cache.remove(domainName);
					}
				}
			}
		}
	}

	private long getCurrentSecond() {
		return eventloop.currentTimeMillis() / 1000;
	}

	public int getNumberOfCachedDomainNames() {
		return cache.size();
	}

	public int getNumberOfCachedExceptions() {
		int exceptions = 0;

		for (CachedDnsLookupResult cachedResult : cache.values()) {
			if (!cachedResult.isSuccessful())
				++exceptions;
		}

		return exceptions;
	}

	public String[] getSuccessfullyResolvedDomainNames() {
		ArrayList<String> domainNames = Lists.newArrayList();

		for (Map.Entry<String, CachedDnsLookupResult> entry : cache.entrySet()) {
			if (entry.getValue().isSuccessful()) {
				domainNames.add(entry.getKey());
			}
		}

		return domainNames.toArray(new String[domainNames.size()]);
	}

	public String[] getDomainNamesOfFailedRequests() {
		ArrayList<String> domainNames = Lists.newArrayList();

		for (Map.Entry<String, CachedDnsLookupResult> entry : cache.entrySet()) {
			if (!entry.getValue().isSuccessful()) {
				domainNames.add(entry.getKey());
			}
		}

		return domainNames.toArray(new String[domainNames.size()]);
	}

	public String[] getAllCachedDomainNames() {
		Set<String> domainNames = cache.keySet();
		return domainNames.toArray(new String[domainNames.size()]);
	}
}
