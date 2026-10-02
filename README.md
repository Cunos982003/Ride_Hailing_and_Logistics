# Ride-Hailing & Logistics Platform

Hệ thống Đặt xe & Giao hàng theo yêu cầu theo thời gian thực (Real-time Ride-Hailing & Logistics System) xây dựng theo kiến trúc Microservices đa module trên nền tảng **Java 21** và **Spring Boot 3.3.x**.

---

## 1. Phạm vi dự án & Công nghệ cốt lõi

- **Khu vực hoạt động ban đầu:** Hà Nội (`HAN`) — Tâm tọa độ: Hồ Hoàn Kiếm (`21.0285° N, 105.8542° E`).
- **Nghiệp vụ:** Xe máy, đặt chuyến, theo dõi vị trí GPS thời gian thực, ví điện tử nội bộ (VND).
- **Ngôn ngữ & Framework:** Java 21 (Virtual Threads enabled), Spring Boot 3.3.6, Spring Security 6.
- **Dữ liệu & Truy vấn:** 
  - **JdbcClient / JdbcTemplate** (Không dùng JPA/Hibernate).
  - **PostgreSQL 16** + **Flyway Migration**.
  - **Redis 7** (Redis GEO + Sorted Sets cho High-Throughput Location Tracking).
- **Bảo mật:** JWT (`jjwt 0.12.x`), mật khẩu mã hóa BCrypt, phân quyền RBAC (CUSTOMER / DRIVER), bảo vệ endpoint nội bộ bằng header `X-Internal-Key`.
- **Kiểm thử tích hợp:** **Testcontainers** (PostgreSQL 16 & Redis 7 thật, không mock).

---

## 2. Cấu trúc Module & Cổng dịch vụ

| Module | Cổng | Chức năng chính | Hạ tầng / Công nghệ |
| :--- | :---: | :--- | :--- |
| **`common`** | — | Thư viện dùng chung: Tiện ích JWT (`JwtUtil`), DTO lỗi chuẩn `{code, message}`, model sự kiện. | `jjwt 0.12.6`, Jackson |
| **`core`** | **8000** | Quản lý người dùng, xác thực (Auth), ví điện tử (Wallet), sổ cái giao dịch (Ledger). | PostgreSQL 16, Flyway, JdbcClient, Redis, BCrypt |
| **`gateway`** | **8001** | API Gateway & WebSocket kết nối client thời gian thực. | Spring Web, WebSocket, Redis |
| **`location`** | **8002** | Xử lý stream vị trí tài xế, tìm kiếm tài xế xung quanh trong bán kính (GEOSEARCH). | Redis 7 (GEO + ZSET) |

Mỗi service đều kích hoạt:
- `spring.threads.virtual.enabled=true`
- `server.shutdown=graceful`
- Probe endpoint: `GET /api/v1/health` trả về `{"status":"UP"}`.

---

## 3. Chi tiết các dịch vụ đã triển khai

### A. Location Service (`location` — Port 8002)

Quản lý luồng tọa độ GPS gửi lên từ tài xế với hiệu năng cao bằng Redis GEO và Sorted Set.

#### Khóa Redis:
- `drivers:geo`: Key GEO lưu vị trí tài xế đang rảnh (`lng` trước, `lat` sau) để phục vụ tìm kiếm không gian.
- `drivers:lastseen`: Key ZSET lưu thời điểm ping cuối cùng (`score = epoch millis`) để kiểm soát thời gian trực tuyến.

#### Tính năng chính:
1. **`update(driverId, lat, lng)` & Batch `update(List<LocationUpdate>)`:**
   - Kiểm tra tọa độ hợp lệ (`latitude` ∈ `[-90, 90]`, `longitude` ∈ `[-180, 180]`), bỏ qua nếu ngoài phạm vi.
   - Ghi theo lô tối ưu hiệu năng bằng `redisTemplate.executePipelined(...)` (GEOADD + ZADD).
2. **`findNearby(lat, lng, radiusKm, limit)`:**
   - Sử dụng lệnh Redis **`GEOSEARCH`** (thay thế cho `GEORADIUS` đã deprecated).
   - Trả về danh sách sắp xếp tăng dần theo khoảng cách kèm `distanceMeters`.
   - Tự động lọc bỏ các tài xế có `lastseen` quá 15 giây trước.
3. **Job dọn dẹp định kỳ `@Scheduled(fixedDelay = 5000)`:**
   - Tự động quét và xóa khỏi cả 2 key các tài xế không gửi vị trí quá 15 giây.
4. **`removeDriver(driverId)`:**
   - Xóa tài xế khỏi `drivers:geo` khi nhận chuyến (không còn ở trạng thái rảnh).
5. **Clock Injection:**
   - Sử dụng Spring Bean `Clock` cho phép test can thiệp thời gian tức thì mà không cần `Thread.sleep()`.

#### Endpoint nội bộ (Header `X-Internal-Key`):
- `POST /internal/locations` — Ghi nhận mảng vị trí tài xế theo lô.
- `GET /internal/drivers/nearby?latitude=...&longitude=...&radiusKm=...&limit=...` — Tìm kiếm tài xế gần nhất theo khoảng cách.
- `DELETE /internal/drivers/{id}` — Xóa tài xế khỏi danh sách rảnh khi nhận cuốc.

---

### B. Core Service (`core` — Port 8000)

Quản lý đăng ký, đăng nhập, phân quyền RBAC và ví tiền của tài xế/khách hàng.

#### Quy tắc nghiệp vụ & Bảo mật:
- **Đăng ký:** `POST /api/v1/auth/register` (Role `CUSTOMER` hoặc `DRIVER`). Tự động tạo ví ban đầu (Customer: 500,000₫, Driver: 0₫).
- **Đăng nhập:** `POST /api/v1/auth/login` — Mật khẩu mã hóa BCrypt, trả về JWT Access Token (hạn 1 giờ, sub = userId, role).
- **Phân quyền RBAC:**
  - `/api/v1/rides/**` chỉ cho phép role `CUSTOMER` (Driver gọi trả về `403 Forbidden`).
  - `/api/v1/driver/**` chỉ cho phép role `DRIVER` (Customer gọi trả về `403 Forbidden`).
  - `/internal/**` yêu cầu header `X-Internal-Key`.
  - Token hết hạn / sai chữ ký trả về `401 Unauthorized`.
  - Trùng email đăng ký trả về `409 Conflict`.

---

## 4. Hướng dẫn Build & Chạy kiểm thử

### Yêu cầu môi trường:
- **JDK 21** (Đã cấu hình biến môi trường `JAVA_HOME`).
- **Maven 3.9+** (hoặc sử dụng `mvnw` / `mvnw.cmd`).
- **Docker Desktop** (đang chạy, phục vụ Testcontainers).

### Thiết lập JAVA_HOME (Windows PowerShell):
```powershell
$env:JAVA_HOME = "C:\Users\admin\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

### Chạy toàn bộ Unit & Integration Test:

```powershell
# Chạy toàn bộ test trong tất cả các module
mvn test

# Chạy riêng integration test của module Location (Testcontainers Redis 7)
mvn test -pl location -Dtest=LocationServiceTestcontainersIntegrationTest

# Chạy test API Controller của Location
mvn test -pl location -Dtest=LocationControllerIntegrationTest

# Chạy integration test của module Core (Testcontainers Postgres 16)
mvn test -pl core -Pintegration -Dtest=AuthIntegrationTest
```

---

## 5. Danh mục Testcases tích hợp tiêu biểu

### Location Module (`LocationServiceTestcontainersIntegrationTest`):
1. **Biên khoảng cách 2 km tại Hà Nội:** Tài xế ở 1.99 km ($1990\text{ m}$) được trả về; tài xế ở 2.01 km ($2010\text{ m}$) không được trả về (tính theo công thức cung kinh tuyến & Haversine).
2. **Lọc Stale & Dọn dẹp:** Tài xế dừng gửi 20 giây bị `findNearby` lọc bỏ và bị job `@Scheduled` xóa sạch khỏi cả `drivers:geo` lẫn `drivers:lastseen`.
3. **Ghi đè vị trí:** Vị trí mới của cùng 1 tài xế ghi đè hoàn toàn vị trí cũ, không tạo bản ghi trùng lặp.
4. **Đảo nhầm tọa độ Lat/Lng:** Vị trí thực tại Hà Nội (`21.0285° N, 105.8542° E`) được xác nhận chính xác; khi đảo nhầm tọa độ sẽ bị từ chối hoặc không xuất hiện trong khu vực Hà Nội.
5. **Ghi lô 1000 điểm:** Ghi đồng thời 1000 tọa độ qua pipeline và truy vấn lại đầy đủ 1000 điểm không thất thoát.

### Core Module (`AuthIntegrationTest`):
1. Đăng ký Customer / Driver thành công & tự động cấp ví.
2. Đăng nhập thành công với BCrypt hash.
3. Đăng ký trùng email trả về `409 Conflict`.
4. Token hết hạn / sai chữ ký trả về `401 Unauthorized`.
5. Khách gọi endpoint tài xế / Tài xế gọi endpoint khách trả về `403 Forbidden`.
6. Endpoint `/internal/**` bảo vệ bởi `X-Internal-Key`.
