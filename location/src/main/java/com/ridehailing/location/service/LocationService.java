package com.ridehailing.location.service;

import com.ridehailing.location.dto.Candidate;
import com.ridehailing.location.dto.LocationUpdate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LocationService {

  private static final Logger log = LoggerFactory.getLogger(LocationService.class);
  private static final String GEO_KEY = "drivers:geo";
  private static final String LASTSEEN_KEY = "drivers:lastseen";
  private static final long STALE_THRESHOLD_MS = 15_000;

  private final StringRedisTemplate redisTemplate;
  private final Clock clock;

  public LocationService(StringRedisTemplate redisTemplate, Clock clock) {
    this.redisTemplate = redisTemplate;
    this.clock = clock;
  }

  // Update location for a single driver or batch of drivers
  public void update(List<LocationUpdate> updates) {
    long now = clock.millis();

    // Filter valid coordinates
    List<LocationUpdate> valid = updates.stream()
        .filter(LocationUpdate::isValidCoordinate)
        .toList();

    if (valid.isEmpty()) {
      return;
    }

    // Use pipeline for batch operations
    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
      for (LocationUpdate update : valid) {
        String member = update.driverId().toString();
        // GEOADD: lng FIRST, lat SECOND
        connection.geoCommands().geoAdd(
            GEO_KEY.getBytes(),
            new Point(update.longitude(), update.latitude()),
            member.getBytes()
        );
        // ZADD lastseen
        connection.zSetCommands().zAdd(
            LASTSEEN_KEY.getBytes(),
            now,
            member.getBytes()
        );
      }
      return null;
    });
  }

  // Find nearby drivers within radius, sorted by distance
  public List<Candidate> findNearby(double lat, double lng, double radiusKm, int limit) {
    long now = clock.millis();
    long cutoff = now - STALE_THRESHOLD_MS;

    // GEOSEARCH (FROMLONLAT lng lat BYRADIUS km ASC COUNT limit WITHDIST)
    Circle circle = new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS));

    GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo()
        .radius(GEO_KEY, circle, RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
            .includeDistance()
            .sortAscending()
            .limit(limit * 2) // Fetch more to account for filtering
        );

    if (results == null) {
      return List.of();
    }

    // Filter by lastseen and collect candidates
    List<Candidate> candidates = new ArrayList<>();
    for (var result : results) {
      String driverIdStr = result.getContent().getName();

      // Check lastseen
      Double lastSeenScore = redisTemplate.opsForZSet().score(LASTSEEN_KEY, driverIdStr);
      if (lastSeenScore == null || lastSeenScore < cutoff) {
        continue;
      }

      UUID driverId = UUID.fromString(driverIdStr);
      double distanceMeters = result.getDistance().getValue() * 1000; // km to meters

      candidates.add(new Candidate(driverId, distanceMeters));

      if (candidates.size() >= limit) {
        break;
      }
    }

    return candidates;
  }

  // Remove driver from geo index (when accepting a trip)
  public void removeDriver(UUID driverId) {
    String member = driverId.toString();
    redisTemplate.opsForGeo().remove(GEO_KEY, member);
    redisTemplate.opsForZSet().remove(LASTSEEN_KEY, member);
  }

  // Scheduled job to clean stale drivers every 5 seconds
  @Scheduled(fixedDelay = 5000)
  public void cleanStaleDrivers() {
    long now = clock.millis();
    long cutoff = now - STALE_THRESHOLD_MS;

    // Find stale driver IDs from ZSET
    var staleDrivers = redisTemplate.opsForZSet()
        .rangeByScore(LASTSEEN_KEY, 0, cutoff);

    if (staleDrivers == null || staleDrivers.isEmpty()) {
      return;
    }

    log.info("Removing {} stale drivers", staleDrivers.size());

    // Remove from both keys
    for (String driverId : staleDrivers) {
      redisTemplate.opsForGeo().remove(GEO_KEY, driverId);
      redisTemplate.opsForZSet().remove(LASTSEEN_KEY, driverId);
    }
  }
}
