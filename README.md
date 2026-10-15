# Telehealth Hospital Management - Hệ Thống Quản Lý Bệnh Viện & Khám Trực Tuyến

Hệ thống Telehealth Hospital Management là giải pháp nền tảng y tế số toàn diện theo kiến trúc **Microservices**, xây dựng trên nền tảng **Java 17 (Spring Boot 3.3.5)** và **React (TypeScript + Vite)**. Hệ thống cung cấp dịch vụ khám bệnh trực tuyến từ xa (Telehealth) kết nối bệnh nhân và bác sĩ thông qua video thời gian thực chuẩn WebRTC (với Coturn STUN/TURN), chat mã hóa đầu cuối AES-256-GCM, quản lý hồ sơ tiền khám, đặt và đổi lịch khám nguyên tử chống tranh chấp, mô phỏng thanh toán có thời hạn giữ chỗ 7 phút và gửi email thông báo xác nhận tự động.

---

## 1. Kiến Trúc Microservices & Công Nghệ

Hệ thống được thiết kế theo đúng tiêu chuẩn microservices: **Mỗi service sở hữu cơ sở dữ liệu riêng, database user riêng và Flyway migration độc lập**. Tuyệt đối không chia sẻ trực tiếp database, không dùng foreign key xuyên service và không join bảng xuyên database.

```
                                  +-------------------+
                                  |   Web Client UI   |
                                  |  (React/Vite/TS)  |
                                  +---------+---------+
                                            |
                                            | :8080 (REST / WS)
                                            v
                                 +---------------------+
                                 |     API Gateway     |
                                 | (Spring Cloud GW)   |
                                 +----------+----------+
                                            |
         +-----------------+----------------+-----------------+-----------------+
         |                 |                |                 |                 |
         v                 v                v                 v                 v
+-----------------+ +----------------+ +----------------+ +----------------+ +-----------------+
| Identity Service| |  Appointment   | |    Clinical    | |    Billing     | |    Realtime     |
|     (:8081)     | | Service (:8082)| | Service (:8083)| | Service (:8084)| | Service (:8085) |
+--------+--------+ +-------+--------+ +-------+--------+ +-------+--------+ +--------+--------+
         |                  |                  |                  |                  |
         v                  v                  v                  v                  v
+-----------------+ +----------------+ +----------------+ +----------------+ +-----------------+
|   identity_db   | | appointment_db | |  clinical_db   | |   billing_db   | |   realtime_db   |
+-----------------+ +----------------+ +----------------+ +----------------+ +-----------------+
                            |                                     |                  ^
                            +-------------+         +-------------+                  |
                                          |         |                                |
                                          v         v                                |
                                   +-------------------+                             |
                                   |     RabbitMQ      |                             |
                                   |  (Event Bus/AMQP) |                             |
                                   +---------+---------+                             |
                                             |                                       |
                                             v                                       |
                                   +-------------------+                             |
                                   |   Notification    |                             |
                                   |  Service (:8086)  |                             |
                                   +---------+---------+                             |
                                             |                                       |
                                             v                                       |
                                   +-------------------+                             |
                                   |  notification_db  |                             |
                                   +-------------------+                             |
                                             |                                       v
                                             v                              +------------------+
                                   +-------------------+                    |      Coturn      |
                                   | Mailpit / SMTP    |                    |  (STUN/TURN)     |
                                   +-------------------+                    +------------------+
```

### Công Nghệ Sử Dụng

- **Ngôn ngữ & Nền tảng:** Java 17, Spring Boot 3.3.5, Spring Cloud 2023.0.3, Gradle 8.14.
- **Frontend:** React 18, TypeScript, Vite, Lucide Icons, Vanilla CSS Design System.
- **Bảo mật:** Spring Security, JWT (HMAC-SHA256, JJWT 0.12.6), BCrypt, AES-256-GCM mã hóa dữ liệu y tế và file.
- **Cơ sở dữ liệu & Migration:** PostgreSQL 16 (6 databases riêng rẽ), Flyway Migration.
- **Caching & Locking:** Redis 7 (Pessimistic locking & atomic transactions).
- **Message Broker:** RabbitMQ 3 (AMQP Outbox Pattern, Deduplication).
- **Giao tiếp Realtime:** Spring WebSocket (TextWebSocketHandler), WebRTC 1-1, Coturn (STUN/TURN relay server).
- **Email:** Spring Mail qua SMTP (bản demo dùng Resend). Mailpit là công cụ thử tùy chọn, không tham gia luồng gửi thư thật.
- **Container hóa & CI/CD:** Dockerfile đa tầng, Docker Compose, GitHub Actions CI & GitHub Container Registry (GHCR).

---

## 2. Tài Khoản Demo (Seed Data Môi Trường Thử Nghiệm)

Hệ thống đã được thiết lập sẵn các tài khoản demo để tiện cho việc kiểm thử và chấm đồ án. Giao diện Web cung cấp thanh **1-Click Demo Switcher** trên thanh điều hướng để chuyển đổi vai trò ngay lập tức:

| Vai trò | Họ tên | Email | Mật khẩu (Hỗ trợ cả 2 dạng) | Ghi chú |
| :--- | :--- | :--- | :--- | :--- |
| **Quản trị viên (ADMIN)** | Quản trị Hệ thống | `admin@demo.com` | `admin123` hoặc `Admin123!` | Quản lý bác sĩ, chuyên khoa, tạo slot, xem log email |
| **Bác sĩ (DOCTOR)** | BS. Nguyễn Thị Ánh | `doctor.anh@demo.com` | `doctor123` hoặc `Doctor123!` | Chuyên khoa Tim mạch (12 năm KN), xem lịch, khám video, upload kết luận |
| **Bệnh nhân 1 (PATIENT)** | Trần Quốc Minh | `patient1@demo.com` | `patient123` hoặc `Patient123!` | Đặt lịch khám, thanh toán giữ chỗ 7 phút, đổi ca, video call, tải báo cáo |
| **Bệnh nhân 2 (PATIENT)** | Lê Thu Hương | `patient2@demo.com` | `patient123` hoặc `Patient123!` | Thử nghiệm tranh chấp ca khám cùng lúc hoặc đổi ca |

---

## 3. Phân Quyền & Thiết Kế Cơ Sở Dữ Liệu Riêng Biệt

Tất cả các service chạy với tài khoản database riêng biệt, không thể truy cập chéo dữ liệu của nhau:

| Dịch vụ | Tên Database | DB Username | Cổng Service | Mục đích lưu trữ |
| :--- | :--- | :--- | :--- | :--- |
| `identity-service` | `identity_db` | `identity_user` | `8081` | Tài khoản, mật khẩu băm BCrypt, vai trò, phiên refresh token |
| `appointment-service` | `appointment_db` | `appointment_user` | `8082` | Bác sĩ, chuyên khoa, ca khám (slots), lịch hẹn, lịch sử đổi ca, outbox events |
| `clinical-service` | `clinical_db` | `clinical_user` | `8083` | Biểu mẫu tiền khám (mã hóa AES-GCM), metadata báo cáo khám bệnh, audit logs |
| `billing-service` | `billing_db` | `billing_user` | `8084` | Đơn thanh toán mô phỏng, lịch sử giao dịch idempotency, outbox events |
| `realtime-service` | `realtime_db` | `realtime_user` | `8085` | Phòng tư vấn (rooms), tin nhắn chat mã hóa AES-GCM, deduplication ID |
| `notification-service`| `notification_db` | `notification_user`| `8086` | Nhật ký email gửi (sent/pending/failed), số lần retry, consumer deduplication |
| `api-gateway` | *(Không dùng DB)* | *(Không có)* | `8080` | Định tuyến ngược (Reverse Proxy), lọc và xác thực JWT tập trung |

---

## 4. Các Luồng Nghiệp Vụ Cốt Lõi & Cơ Chế Đảm Bảo An Toàn

### A. Luồng Đặt Lịch & Giữ Chỗ 7 Phút (Pending Payment)
1. Bệnh nhân điền **Biểu mẫu thông tin trước khám** (Lý do khám, triệu chứng, tiền sử, bệnh nền, thuốc đang dùng, dị ứng). Biểu mẫu được mã hóa `AES-256-GCM` trước khi lưu vào `clinical_db`.
2. Bệnh nhân chọn Bác sĩ, Ngày khám và Ca khám (Slot) còn trống.
3. Khi bấm **Giữ ca**:
   - Backend sử dụng **PostgreSQL row-level locking** (`SELECT ... FOR UPDATE`) kết hợp unique constraint trên bảng `slot_claims` để đảm bảo: **Nếu 2 bệnh nhân cùng bấm đặt 1 ca cùng lúc, chỉ duy nhất 1 người thành công**.
   - Trạng thái ca chuyển thành `HOLD`, lịch hẹn tạo ở trạng thái `PENDING_PAYMENT` với thời hạn cố định **chính xác 7 phút** từ server (`expires_at = now + 7 minutes`).
   - Tải lại trang (F5) không làm gia hạn thời gian giữ chỗ; client đồng bộ thời gian còn lại trực tiếp từ mốc `paymentExpiresAt` của server.
   - Background worker (`AppointmentExpiryScheduler`) chạy định kỳ quét các ca quá 7 phút chưa thanh toán, tự động chuyển thành `EXPIRED` và giải phóng slot về trạng thái `AVAILABLE`.
4. Màn hình thanh toán hiển thị đồng hồ đếm ngược, mã QR minh họa và số tiền chuẩn **100.000 VNĐ**.
5. Nhấn **Mô phỏng Thanh toán Thành công**:
   - `billing-service` ghi nhận giao dịch idempotency, kiểm tra thời hạn 7 phút.
   - Kích hoạt sự kiện xác nhận `appointment-service` chuyển lịch sang `CONFIRMED`.
   - Yêu cầu gửi email được lưu cùng giao dịch xác nhận, sau commit được chuyển tới `notification-service` qua API nội bộ; Spring Mail gửi bằng SMTP đã cấu hình.

### B. Quy Tắc Đổi Ca Khám (Reschedule)
- Bệnh nhân không được tự ý hủy lịch (theo yêu cầu nghiệp vụ).
- **Chỉ cho phép đổi ca với lịch đã `CONFIRMED`**.
- **Chỉ đổi ca cùng Bác sĩ**, giữ nguyên thông tin thanh toán và biểu mẫu bệnh án ban đầu.
- Không giới hạn số lần đổi ca và hoàn toàn **miễn phí**.
- **Khóa đổi ca theo Múi giờ Việt Nam (`Asia/Ho_Chi_Minh`)**: Chỉ được đổi ca trước ngày khám. Kể từ **00:00 ngày khám**, tính năng đổi ca bị khóa chặt ở cả giao diện Web lẫn API backend.
- Ca mới phải là ca trống và ở ngày khám trong tương lai. Thao tác đổi ca diễn ra nguyên tử (atomic transaction) - nếu ca mới bị tranh chấp, lịch cũ vẫn được giữ nguyên vẹn.

### C. Khám Trực Tuyến & WebRTC Video 1-1
- Mỗi lịch hẹn sở hữu duy nhất 1 phòng tư vấn an toàn (`Room`). Chỉ Bác sĩ phụ trách và Bệnh nhân của lịch hẹn mới có thể xin cấp Admission Token để vào phòng.
- **WebRTC Video 1-1**:
  - Hỗ trợ bật/tắt camera (khi tắt camera cuộc gọi thoại âm thanh vẫn tiếp tục bình thường).
  - Hỗ trợ bật/tắt micro.
  - Tích hợp máy chủ **Coturn** hỗ trợ STUN/TURN relay đảm bảo kết nối xuyên qua NAT/Firewall.
  - Hiển thị trạng thái hiện diện (Presence): Báo rõ đối phương đã vào phòng hay đang ở phòng chờ.
- **Chat Mã Hóa Đầu Cuối & Chống Trùng**:
  - Tin nhắn gửi qua WebSocket, mã hóa AES-256-GCM khi lưu trữ.
  - Kèm `clientMessageId` (UUID) đảm bảo khi mất kết nối và kết nối lại (reconnect) không phát sinh tin nhắn trùng lặp.
- **Ràng Buộc Trạng Thái Bác Sĩ**: Mỗi bác sĩ chỉ được phép có duy nhất **1 ca khám ở trạng thái `IN_PROGRESS`** tại một thời điểm.

### D. Tải Báo Cáo Kết Luận & Hoàn Tất Ca Khám
- Bác sĩ tải lên tệp báo cáo kết luận y tế (hỗ trợ DOCX, PDF, ZIP dung lượng tối đa 20 MB). File được mã hóa AES-256-GCM và lưu an toàn trong volume lưu trữ riêng tư, database chỉ lưu trữ metadata.
- Bác sĩ thực hiện **Công bố báo cáo (Publish)**.
- Ca khám chỉ được chuyển sang `COMPLETED` sau khi đã có ít nhất một báo cáo được công bố.
- Sau khi ca khám hoàn tất:
  - Hệ thống lưu `COMPLETED` rồi đóng phòng; người còn trong phòng được thông báo, tắt camera/micro và quay về lịch khám.
  - Người đã rời phòng không bị điều hướng hoặc nhận thông báo bất ngờ.
  - Không thể vào lại phòng hoặc gửi tin nhắn mới, kể cả dùng vé vào phòng đã cấp trước đó.
  - Lịch sử chat được chuyển sang trạng thái **chỉ đọc (Read-only)**.
  - Bệnh nhân có thể xem và tải về toàn bộ file báo cáo kết luận của mình.
  - Nếu dịch vụ phòng khám tạm mất kết nối, yêu cầu đóng phòng được lưu để thử lại; giao diện trong phòng cũng kiểm tra trạng thái mỗi 3 giây.
- Giao tiếp nội bộ giữa appointment/realtime dùng cùng biến `INTERNAL_SERVICE_TOKEN` (cần đặt giá trị riêng khi triển khai ngoài local).

### E. Email Xác Nhận & An Toàn Thông Tin
- Gửi email tự động khi:
  1. Xác nhận đặt lịch khám thành công sau thanh toán.
  2. Xác nhận đổi ca khám thành công (ghi rõ thời gian ca cũ và ca mới).
- **Nguyên tắc bảo vệ dữ liệu y tế**: Email thông báo tuyệt đối **không chứa triệu chứng, chẩn đoán, bệnh sử hay đơn thuốc** của bệnh nhân.
- Bản demo hiện gửi qua **Resend**, người gửi `onboarding@resend.dev`. **Mailpit** chỉ dùng khi cần thử email offline, không cần chạy khi dùng SMTP thật.
- Chỉ có hai loại thư: xác nhận thanh toán/đặt lịch và xác nhận đổi ca; không có thư kích hoạt tài khoản.
- Sau khi giao dịch thành công, yêu cầu gửi được lưu trong outbox và gửi sau commit. Nếu dịch vụ email tạm lỗi, hệ thống thử lại; mỗi lần đổi ca có mã sự kiện riêng để tránh gửi trùng khi thử lại.
- Người nhận mặc định lấy từ tài khoản bệnh nhân trong identity database. Đặt `DEMO_NOTIFICATION_EMAIL` để chuyển riêng thông báo của hai bệnh nhân demo sang một hộp thư thật; không đổi email đăng nhập và không ảnh hưởng bệnh nhân khác.
- Với cấu hình mẫu Mailpit, mở [Mailpit](http://localhost:8025) để đọc thư thử (không gửi ra Internet). SMTP trong Docker là `mailpit:1025`, không cần tài khoản/mật khẩu.
- Quản trị viên có thể xem trạng thái ở **Nhật ký Email & Vận hành**. Chỉ các giao dịch mới phát sinh sau khi bật tính năng mới tạo thư, không tự gửi bù lịch demo cũ.
- Kiểm tra cả hai bệnh nhân demo: `node tools/appointment-email-smoke.mjs`. Công cụ tạo và dọn lịch thử riêng, giữ thư mẫu trong Mailpit để xem.

#### Nhận thông báo demo ở Gmail thật

- File `.env` cục bộ đã được Git bỏ qua; chỉ commit `.env.example`. Không gửi mật khẩu vào chat hoặc đưa lên GitHub.
- Đặt `DEMO_NOTIFICATION_EMAIL` thành email nhận thư. Hai email đăng nhập demo vẫn giữ nguyên.
- Với Resend: host `smtp.resend.com`, cổng `587`, username `resend`, password là API key Resend, `MAIL_FROM=onboarding@resend.dev`; bật xác thực và STARTTLS (cả `ENABLE` và `REQUIRED`). Người nhận demo phải là email đăng ký tài khoản Resend khi dùng địa chỉ gửi thử này.
- Gmail SMTP: `SPRING_MAIL_HOST=smtp.gmail.com`, `SPRING_MAIL_PORT=587`, bật `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` và `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED` thành `true`.
- `SPRING_MAIL_USERNAME` là tài khoản gửi; `SPRING_MAIL_PASSWORD` là **mật khẩu ứng dụng** của tài khoản đó, không phải mật khẩu đăng nhập Gmail. `MAIL_FROM` dùng cùng email tài khoản gửi. Xem [hướng dẫn Google về mật khẩu ứng dụng](https://support.google.com/mail/answer/185833?hl=vi).
- Sau khi điền thông tin trên máy, áp dụng bằng `docker compose up -d --no-deps notification-service`. Compose cần tạo lại container để đọc `.env`; chỉ `restart` không cập nhật biến môi trường.
- Khi thiếu thông tin SMTP, thư mới được giữ ở `FAILED` để thử lại, không bị báo nhầm `SENT`. Nhật ký quản trị hiện lý do lỗi. Thư đã gửi vào Mailpit trước đó không tự gửi lại ra Gmail.
- Công cụ smoke phía trên chỉ chạy với Mailpit và sẽ từ chối tạo thư thử khi đang dùng SMTP thật.

#### Mailpit tùy chọn, không chạy cùng bản gửi thư thật

- Service `mailpit` thuộc profile `mailpit`; `notification-service` không phụ thuộc vào nó.
- Để thử offline, dùng các giá trị SMTP Mailpit trong `.env.example`, đặt `COMPOSE_PROFILES=mailpit` rồi chạy Compose. Hoặc bật riêng bằng `docker compose --profile mailpit up -d mailpit`.
- Khi dùng Resend, không đặt `COMPOSE_PROFILES=mailpit`. Nhật ký trên giao diện chỉ có nút **Làm mới**, không dẫn tới hộp thư thử.

---

## 5. Hướng Dẫn Chạy Local

### Cách 1: Khởi Chạy Toàn Bộ Hệ Thống Bằng Docker Compose (Khuyên dùng)

Yêu cầu: Docker Desktop đã bật và hoạt động.

Lần đầu chạy, sao chép `.env.example` thành `.env` để chọn chế độ email: bản mẫu bật profile Mailpit để thử offline; nếu dùng Resend/SMTP thật thì bỏ `COMPOSE_PROFILES=mailpit` và điền cấu hình gửi riêng. Không ghi đè `.env` nếu đã có API key.

1. Khởi chạy hạ tầng (Postgres 6 DBs, Redis, RabbitMQ, Coturn), backend microservices và web frontend. Mailpit chỉ chạy nếu bật profile thử mail:
   ```bash
   docker compose up --build -d
   ```
2. Kiểm tra trạng thái các container:
   ```bash
   docker compose ps
   ```
3. Truy cập các địa chỉ dịch vụ:
   - **Giao diện Web Ứng dụng:** [http://localhost:5173](http://localhost:5173) hoặc [http://localhost:3000](http://localhost:3000)
   - **API Gateway:** [http://localhost:8080](http://localhost:8080)
   - **Mailpit Web UI (chỉ khi bật profile thử mail):** [http://localhost:8025](http://localhost:8025)
   - **RabbitMQ Management Dashboard:** [http://localhost:15672](http://localhost:15672) (User: `guest`, Pass: `guest`)

---

### Cách 2: Khởi Chạy Từng Phần Phục Vụ Phát Triển (Dev Mode)

Nếu muốn chạy hạ tầng trên Docker và chạy mã nguồn Java / React trực tiếp trên máy:

1. Khởi động hạ tầng nền tảng:
   ```bash
   docker compose up postgres redis rabbitmq -d
   ```
   Nếu cần hộp thư thử offline: `docker compose --profile mailpit up -d mailpit`. Không cần bước này khi dùng Resend.
2. Chạy lần lượt các backend microservices (sử dụng Gradle Wrapper trên Java 17):
   ```bash
   # Terminal 1: Identity Service
   ./gradlew :services:identity-service:bootRun

   # Terminal 2: Appointment Service
   ./gradlew :services:appointment-service:bootRun

   # Terminal 3: Clinical Service
   ./gradlew :services:clinical-service:bootRun

   # Terminal 4: Billing Service
   ./gradlew :services:billing-service:bootRun

   # Terminal 5: Realtime Service
   ./gradlew :services:realtime-service:bootRun

   # Terminal 6: Notification Service
   ./gradlew :services:notification-service:bootRun

   # Terminal 7: API Gateway
   ./gradlew :services:api-gateway:bootRun
   ```
3. Khởi động Frontend Web:
   ```bash
   cd apps/web
   npm install
   npm run dev
   ```
   Mở trình duyệt tại [http://localhost:5173](http://localhost:5173).

---

## 6. Hướng Dẫn Kiểm Thử & Build

### Kiểm Thử Toàn Bộ Backend Test Suite
Chạy toàn bộ unit test và integration test của tất cả các microservices:
```bash
./gradlew test
```
*Kết quả: 36 tasks kiểm thử đều thành công tuyệt đối.*

### Đóng Gói Toàn Bộ Executable JARs
```bash
./gradlew bootJar
```
Các file JAR độc lập được tạo ra tại:
- `services/api-gateway/build/libs/api-gateway.jar`
- `services/identity-service/build/libs/identity-service.jar`
- `services/appointment-service/build/libs/appointment-service.jar`
- `services/clinical-service/build/libs/clinical-service.jar`
- `services/billing-service/build/libs/billing-service.jar`
- `services/realtime-service/build/libs/realtime-service.jar`
- `services/notification-service/build/libs/notification-service.jar`

### Đóng Gói Frontend Production Bundle
```bash
cd apps/web
npm run build
```
Bản build tối ưu hóa nằm tại thư mục `apps/web/dist/`.

---

## 7. Quy Trình GitHub Actions CI/CD & GitHub Container Registry (GHCR)

Dự án đã được cấu hình hoàn chỉnh workflow CI/CD tại file [`.github/workflows/ci.yml`](.github/workflows/ci.yml):

### A. Kịch Bản Pull Request (PR Validation)
- Tự động kích hoạt khi có Pull Request vào nhánh `main` hoặc `master`.
- Chạy toàn bộ test backend và đóng gói `bootJar`.
- Kiểm tra lint, typescript và build frontend production bundle.
- Kiểm tra khả năng build của các Dockerfiles.
- **Không yêu cầu secret production, không push container images lên registry**.

### B. Kịch Bản Push to Main hoặc Kích Hoạt Thủ Công (`workflow_dispatch`)
- Chạy toàn bộ quy trình kiểm thử và build độc lập.
- Khi các bước kiểm thử vượt qua, ma trận Docker (`matrix`) sẽ build và publish độc lập từng image lên **GitHub Container Registry (GHCR)**:
  - `ghcr.io/<owner>/<repo>-api-gateway:sha-<commit>`
  - `ghcr.io/<owner>/<repo>-identity-service:sha-<commit>`
  - `ghcr.io/<owner>/<repo>-appointment-service:sha-<commit>`
  - `ghcr.io/<owner>/<repo>-clinical-service:sha-<commit>`
  - `ghcr.io/<owner>/<repo>-billing-service:sha-<commit>`
  - `ghcr.io/<owner>/<repo>-realtime-service:sha-<commit>`
  - `ghcr.io/<owner>/<repo>-notification-service:sha-<commit>`
  - `ghcr.io/<owner>/<repo>-web:sha-<commit>`
- Tự động gắn tag `latest` cho các bản build trên nhánh `main`.
- Đăng nhập GHCR bằng `secrets.GITHUB_TOKEN` với quyền tối thiểu: `contents: read` và `packages: write`.
- Lưu trữ các executable JARs và frontend build làm **Workflow Artifacts** trong 14 ngày.

### Cách Xem Artifacts và Pull Docker Images Từ GHCR
1. **Xem và tải JARs:** Truy cập tab **Actions** trên repository GitHub -> Chọn lượt chạy workflow thành công -> Kéo xuống mục **Artifacts** để tải file `backend-jars` và `frontend-dist`.
2. **Tìm packages trên GHCR:** Tại trang chính của GitHub Repository, xem mục **Packages** ở thanh điều hướng bên phải.
3. **Kéo (pull) image về máy:**
   ```bash
   docker pull ghcr.io/<owner>/<repo>-appointment-service:sha-<commit>
   ```
4. **Cấp quyền đọc Package:** Nếu repository ở chế độ Private, vào `Package Settings` -> `Manage Actions access` hoặc `Change visibility` để cấp quyền truy cập.

---

## 8. Báo Cáo Nghiệm Thu & Các Lưu Ý Kỹ Thuật

- **Môi trường Java:** Xác nhận sử dụng Java 17 theo đúng chỉ đạo kỹ thuật.
- **Tính độc lập của Database:** 6 databases với 6 user riêng biệt và 6 bộ Flyway migration riêng biệt. Không chia sẻ thực thể JPA hay truy vấn cross-DB.
- **WebRTC Local vs Production:** Trên môi trường máy cục bộ (cùng localhost hoặc mạng LAN), WebRTC hoạt động thông qua Direct P2P và Coturn STUN/TURN server cục bộ. Khi triển khai production trên mạng Internet công cộng với 2 thiết bị khác mạng (khác NAT/Firewall), cần cấu hình IP ngoại mạng công khai vào file cấu hình Coturn `turnserver.conf`.
- **Trạng thái GitHub Remote:** Workflow CI/CD đã được định nghĩa đầy đủ và kiểm tra cú pháp; workflow sẽ tự động chạy ngay khi repository được push lên GitHub remote.
