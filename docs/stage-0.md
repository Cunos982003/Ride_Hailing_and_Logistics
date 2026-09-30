# Giai đoạn 0 — Nền tảng

## Đã triển khai

- [x] Chốt MVP: một loại xe **xe máy**, một thành phố **Hà Nội**, thanh toán **ví nội bộ/VND**.
- [x] Parent Maven và đúng tám module; `common` là library, bảy module còn lại là executable apps.
- [x] DTO, event envelope/schema, mã lỗi JSON và JWT RS256 utilities dùng chung, có unit tests.
- [x] Package conventions và REST `/api/v1` được ghi trong [conventions.md](conventions.md).
- [x] Bật virtual threads và keep-alive cho mọi app; có test request chạy trên virtual thread,
  ghi chú tránh `synchronized` quanh blocking I/O trên Java 21.
- [x] Spotless, Checkstyle, Maven Enforcer, Wrapper checksum, pre-commit hook và setup scripts.
- [x] Compose PostgreSQL 16/PostGIS + Redis 7, database/tài khoản riêng và Flyway foundation.
- [x] Profile local không cần hạ tầng; profile infra và Testcontainers dành cho kết nối thật.
- [x] CI verify/integration và hướng dẫn Windows/Linux/IntelliJ.

## Checklist kiểm chứng

Chỉ đánh dấu hoàn thành sau khi đã chạy thực tế; kết quả được cập nhật cuối triển khai.

- [ ] `mvn verify` dưới JDK 21 xanh toàn reactor.
- [ ] `./mvnw verify` và `mvnw.cmd verify` xanh.
- [ ] `./mvnw verify -Pintegration` xanh, không skip integration tests.
- [ ] Compose hợp lệ; PostgreSQL/PostGIS và Redis healthy.
- [ ] Docker build và bảy ứng dụng healthy với profile infra.
- [ ] Gateway chuyển tiếp đúng status tới sáu upstream.
- [ ] Database isolation được kiểm tra, Flyway migration validate/migrate idempotent.
- [ ] Hook chạy được; IDE build/diagnostics và `git diff --check` đã kiểm tra.

## Chưa thuộc Giai đoạn 0

Login/đăng ký và authorization filter; GPS ingest/Redis GEO/H3; WebSocket handshake,
streaming và Pub/Sub; matching/state machine/locking; pricing/surge; wallet ledger,
payment transaction/idempotency; Kafka/RabbitMQ/outbox; client mobile/web; load test,
VPS, TLS và deploy. `TripCompletedEvent` mới là hợp đồng, chưa có producer/consumer.

Thời gian 2–3 ngày là ước lượng roadmap, không phải bằng chứng mọi thành viên đã build
trên máy riêng. Thành viên cần chạy hướng dẫn README với JDK 21 và CI là kiểm tra tái lập.
