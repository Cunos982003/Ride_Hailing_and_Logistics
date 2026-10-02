package com.ridehailing.gateway.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.gateway.websocket.dto.LocationUpdate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class LocationBatchProcessor {
  private static final Logger log = LoggerFactory.getLogger(LocationBatchProcessor.class);

  private final BlockingQueue<LocationUpdate> locationQueue = new LinkedBlockingQueue<>();
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final String locationServiceUrl;
  private final String internalKey;

  public LocationBatchProcessor(
      RestTemplate restTemplate,
      ObjectMapper objectMapper,
      @Value("${location.service.url:http://localhost:8082}") String locationServiceUrl,
      @Value("${internal.key:secret}") String internalKey) {
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
    this.locationServiceUrl = locationServiceUrl;
    this.internalKey = internalKey;
  }

  public void enqueue(LocationUpdate update) {
    if (!locationQueue.offer(update)) {
      log.warn("Location queue full, dropping update for driver {}", update.driverId());
    }
  }

  @Scheduled(fixedDelay = 200)
  public void processBatch() {
    List<LocationUpdate> batch = new ArrayList<>();
    locationQueue.drainTo(batch, 100);

    if (batch.isEmpty()) {
      return;
    }

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-Internal-Key", internalKey);

      String json = objectMapper.writeValueAsString(batch);
      HttpEntity<String> request = new HttpEntity<>(json, headers);

      restTemplate.exchange(
          locationServiceUrl + "/internal/locations", HttpMethod.POST, request, Void.class);

      log.debug("Sent {} location updates to location service", batch.size());
    } catch (Exception e) {
      log.error("Failed to send location batch to location service", e);
    }
  }
}
