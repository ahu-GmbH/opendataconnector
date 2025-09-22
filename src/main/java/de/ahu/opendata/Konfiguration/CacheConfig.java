package de.ahu.opendata.Konfiguration;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class CacheConfig {
	@Bean
	CacheManager cacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager();

		Map<String, Cache<Object, Object>> caches = new HashMap<>();

		Cache<Object, Object> wetterstationenCache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES)
				.maximumSize(1000).build();

		Cache<Object, Object> pegelstationCache = Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES)
				.maximumSize(500).build();

		caches.put("wetterstationen", wetterstationenCache);
		caches.put("pegelstation", pegelstationCache);

		Iterator<Entry<String, Cache<Object, Object>>> iterator = caches.entrySet().iterator();

		while (iterator.hasNext()) {
			Entry<String, Cache<Object, Object>> entry = iterator.next();
			String cacheName = entry.getKey();
			Cache<Object, Object> cache = entry.getValue();

			cacheManager.registerCustomCache(cacheName, cache);
		}

		return cacheManager;
	}
}
