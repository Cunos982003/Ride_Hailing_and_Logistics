# Quy ước nền tảng

## Phạm vi và ownership

- MVP: `MOTORBIKE`, thành phố `HAN` (Hà Nội), `INTERNAL_WALLET`, `VND`.
- `common` chỉ chứa DTO, hợp đồng event, lỗi và JWT utilities; không chứa entity,
  repository, cấu hình datasource hay nghiệp vụ của service.
- Package gốc `com.ridehailing`; module có dấu gạch ngang nhưng package không có:
  `user`, `location`, `dispatch`, `pricing`, `payment`, `wsgateway`, `apigateway`.
- Mỗi service sở hữu database và Flyway history của mình. Không JOIN, đọc hoặc ghi
  bảng của service khác. Compose dùng chung một PostgreSQL instance **chỉ cho local**.
- Redis key bắt đầu bằng tên service, ví dụ `location-service:driver:<uuid>`.
  Namespace không phải cơ chế phân quyền; ACL riêng sẽ được triển khai trước production.

## REST và JSON

- Prefix `/api/v1`; tài nguyên: `/users`, `/locations`, `/trips`, `/pricing`, `/payments`.
- CamelCase trong JSON; enum `UPPER_SNAKE_CASE`; định danh UUID.
- Timestamp ISO-8601 UTC (`2026-09-29T10:00:00Z`); múi giờ nghiệp vụ
  `Asia/Ho_Chi_Minh` chỉ dùng khi hiển thị/tính quy tắc địa phương.
- Tiền biểu diễn số nguyên đồng VND: `{"amount":25000,"currency":"VND"}`.
  Không dùng `double`/`float` cho tiền. Client JavaScript cần lưu ý giới hạn số nguyên
  an toàn; không chuyển số tiền lớn qua floating point.
- Tọa độ có latitude `[-90,90]`, longitude `[-180,180]`; validation ở API bằng `@Valid`.
  Truy vấn Redis GEO về sau phải xét giới hạn latitude của Redis và vùng phục vụ Hà Nội.
- Thay đổi phá vỡ tương thích cần phiên bản API/event mới; không đổi ý nghĩa trường hiện có.
- Giai đoạn 0 chỉ có status kỹ thuật và actuator; chưa có hợp đồng API nghiệp vụ.

## Định dạng lỗi

HTTP status phản ánh lỗi thực tế. Body luôn có cùng cấu trúc cho lỗi MVC:

```json
{
  "timestamp": "2026-09-29T10:00:00Z",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/users",
  "requestId": "request-123",
  "details": [{"field": "name", "message": "must not be blank"}]
}
```

`ErrorCode` là mã ổn định, client không parse `message`. `details` luôn là array;
không có validation thì `[]`. Không trả stack trace, câu SQL, secrets hoặc rejected value.
`X-Request-Id` được nhận nếu khớp `[A-Za-z0-9_-]{1,64}`, nếu không tạo UUID; lỗi trả lại
header và cùng ID trong body. Giai đoạn này chưa có distributed tracing hay filter
request ID cho mọi response thành công. Lỗi trước MVC (proxy/security/container)
cần được chuẩn hóa khi bổ sung các lớp đó.

`ApiExceptionHandler` được `@Import` rõ ràng tại từng application. Dùng
`ApiException(ErrorCode)` cho lỗi domain có public message đã thống nhất; không dùng
exception message tùy ý từ database/API bên ngoài để trả cho client.

## Event v1

`EventEnvelope<T>` có `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `producer`,
`correlationId`, `payload`. Event `trip.completed` do `dispatch-service` phát, payload
`TripCompletedEvent`. JSON Schema và ví dụ nằm trong
`common/src/main/resources/event-schemas/` và được kiểm thử cùng Java serialization.

Chưa có broker, publish, retry, outbox hoặc consumer. Các giai đoạn sau phải thiết kế
idempotency theo `eventId` và bảo đảm giao dịch trước khi nối Payment vào event.

## JWT

`JwtUtils` dùng Spring Security OAuth2 JOSE/Nimbus để ký và xác minh **RS256**.
Public key RSA tối thiểu 2048 bits; token cần `iss`, `aud`, `sub`, `jti`, `iat`, `exp`.
Decoder kiểm tra chữ ký, issuer, audience, thời hạn và issue time; expiry không có
grace window, issue time cho phép lệch tối đa 30 giây. Đồng bộ clock giữa các máy.

- Private/public keys truyền từ bên ngoài; không commit key thật hay hardcode signing secret.
- Chỉ auth owner có private key; service xác minh chỉ nhận public key.
- Không chấp nhận unsigned JWT hoặc thuật toán tùy ý từ token.
- Utilities **không** tự bật security filter chain, login, phân quyền hay key rotation.
  Các endpoint hiện **chưa được bảo vệ**: chỉ chạy local, không expose ra Internet.

## Virtual threads trên Java 21

Tất cả ứng dụng dùng servlet/Tomcat, gồm API Gateway **Server Web MVC**, không dùng
Gateway WebFlux/Netty. Cấu hình:

```yaml
spring:
  threads:
    virtual:
      enabled: true
  main:
    keep-alive: true
```

Test-only endpoint chứng minh request servlet chạy trên `Thread.isVirtual()`.
Endpoint đó không có trong production JAR. Virtual thread không thay thế pool
database, rate limiting hoặc backpressure; không có cam kết throughput/latency ở Giai đoạn 0.

**Java 21 có pinning:** không giữ `synchronized` trong lúc gọi I/O blocking
(JDBC, HTTP, Redis, file). Khi cần bảo vệ vùng có thể blocking, cân nhắc
`ReentrantLock`, thu nhỏ critical section và không giữ lock qua network call.
Native/foreign calls cũng có thể pin carrier thread. Chẩn đoán bằng JFR và
`-Djdk.tracePinnedThreads=full`; không bật trace đầy đủ thường xuyên trên production.
`spring.main.keep-alive=true` giữ JVM sống khi chỉ còn daemon virtual threads.

## Build và chất lượng

- JDK **21**, Maven **3.9.x**; Wrapper pin **3.9.16** cùng SHA-256.
- Spring Boot **3.5.16**, Spring Cloud **2025.0.3**; dependency/plugin version quản lý ở parent.
- Spotless dùng Google Java Format; Checkstyle kiểm tra import, braces, naming cơ bản.
- `./mvnw verify` không cần Docker. `-Pintegration` chạy `*IT` bằng Failsafe và
  Testcontainers; Docker thiếu phải fail, không dùng `disabledWithoutDocker`.
- Hook pre-commit kiểm tra toàn bộ working tree Java bằng `spotless:check`; không
  tự format hay stage. Hook không thay thế `verify`/CI và không kiểm tra riêng snapshot staged.
- Unit tests không dùng H2 giả PostgreSQL. Integration dùng PostgreSQL/PostGIS 16 và Redis 7 thật.
