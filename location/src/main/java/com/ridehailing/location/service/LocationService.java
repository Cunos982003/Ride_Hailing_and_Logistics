package com.ridehailing.location.service;

import com.ridehailing.location.dto.Candidate;
import com.ridehailing.location.dto.LocationUpdate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LocationService {

  private static final Logger log = LoggerFactory.getLogger(LocationService.class);
  public static final String GEO_KEY = "drivers:geo";
  public static final String LASTSEEN_KEY = "drivers:lastseen";
  public static final long STALE_THRESHOLD_MS = 15_000;

  private final StringRedisTemplate redisTemplate;
  private final Clock clock;

  public LocationService(StringRedisTemplate redisTemplate, Clock clock) {
    this.redisTemplate = redisTemplate;
    this.clock = clock;
  }

  public static boolean isValidCoordinate(double lat, double lng) {
    return lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0;
  }

  // 1) Update single driver location: ignore if invalid coordinate; GEOADD (lng, lat) + ZADD
  // lastseen
  public void update(UUID driverId, double lat, double lng) {
    if (driverId == null || !isValidCoordinate(lat, lng)) {
      return;
    }
    update(List.of(new LocationUpdate(driverId, lat, lng)));
  }

  public void update(String driverId, double lat, double lng) {
    if (driverId == null || driverId.isBlank() || !isValidCoordinate(lat, lng)) {
      return;
    }
    try {
      update(UUID.fromString(driverId), lat, lng);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid UUID for driver: {}", driverId);
    }
  }

  // Update batch of driver locations using executePipelined
  public void update(List<LocationUpdate> updates) {
    if (updates == null || updates.isEmpty()) {
      return;
    }

    List<LocationUpdate> valid =
        updates.stream().filter(u -> u != null && u.isValidCoordinate()).toList();

    if (valid.isEmpty()) {
      return;
    }

    long now = clock.millis();

    // Use executePipelined for batch operations
    redisTemplate.executePipelined(
        (RedisCallback<Object>)
            connection -> {
              for (LocationUpdate update : valid) {
                String member = update.driverId().toString();
                // GEOADD: lng first, lat second
                connection
                    .geoCommands()
                    .geoAdd(
                        GEO_KEY.getBytes(),
                        new Point(update.longitude(), update.latitude()),
                        member.getBytes());
                // ZADD lastseen
                connection.zSetCommands().zAdd(LASTSEEN_KEY.getBytes(), now, member.getBytes());
              }
              return null;
            });
  }

  // 2) Find nearby drivers within radius, sorted by distance ascending using GEOSEARCH,
  // and filter out drivers whose lastseen was > 15 seconds ago.
  public List<Candidate> findNearby(double lat, double lng, double radiusKm, int limit) {
    if (!isValidCoordinate(lat, lng) || radiusKm <= 0 || limit <= 0) {
      return List.of();
    }

    long now = clock.millis();
    long cutoff = now - STALE_THRESHOLD_MS;

    // GEOSEARCH (FROMLONLAT lng lat BYRADIUS radiusKm km ASC WITHDIST)
    GeoReference<String> reference = GeoReference.fromCoordinate(new Point(lng, lat));
    Distance distance = new Distance(radiusKm, Metrics.KILOMETERS);
    RedisGeoCommands.GeoSearchCommandArgs args =
        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().sortAscending();

    args.limit(Math.max((long) limit * 2, 50L));

    GeoResults<RedisGeoCommands.GeoLocation<String>> results =
        redisTemplate.opsForGeo().search(GEO_KEY, reference, distance, args);

    if (results == null) {
      return List.of();
    }

    List<Candidate> candidates = new ArrayList<>();
    for (var result : results) {
      String driverIdStr = result.getContent().getName();

      // Check lastseen in drivers:lastseen
      Double lastSeenScore = redisTemplate.opsForZSet().score(LASTSEEN_KEY, driverIdStr);
      if (lastSeenScore == null || lastSeenScore < cutoff) {
        continue;
      }

      try {
        UUID driverId = UUID.fromString(driverIdStr);
        double distanceMeters;
        if (result.getDistance() != null) {
          if (Metrics.KILOMETERS.equals(result.getDistance().getMetric())) {
            distanceMeters = result.getDistance().getValue() * 1000.0;
          } else {
            distanceMeters = result.getDistance().getValue();
          }
        } else {
          distanceMeters = 0.0;
        }

        candidates.add(new Candidate(driverId, distanceMeters));

        if (candidates.size() >= limit) {
          break;
        }
      } catch (IllegalArgumentException e) {
        log.warn("Invalid UUID in geo key: {}", driverIdStr);
      }
    }

    return candidates;
  }

  // 3) Scheduled job running every 5 seconds to clean drivers whose lastseen is older than 15s from
  // BOTH keys
  @Scheduled(fixedDelay = 5000)
  public void cleanStaleDrivers() {
    long now = clock.millis();
    long cutoff = now - STALE_THRESHOLD_MS;

    Set<String> staleDrivers = redisTemplate.opsForZSet().rangeByScore(LASTSEEN_KEY, 0, cutoff);

    if (staleDrivers == null || staleDrivers.isEmpty()) {
      return;
    }

    log.info("Removing {} stale drivers", staleDrivers.size());

    String[] staleArray = staleDrivers.toArray(new String[0]);
    redisTemplate.opsForGeo().remove(GEO_KEY, staleArray);
    redisTemplate.opsForZSet().remove(LASTSEEN_KEY, (Object[]) staleArray);
  }

  // 4) Remove driver from drivers:geo (when driver accepts a trip)
  public void removeDriver(UUID driverId) {
    if (driverId == null) {
      return;
    }
    removeDriver(driverId.toString());
  }

  public void removeDriver(String driverId) {
    if (driverId == null || driverId.isBlank()) {
      return;
    }
    redisTemplate.opsForGeo().remove(GEO_KEY, driverId);
  }
}
