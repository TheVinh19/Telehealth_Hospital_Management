# TÀI LIỆU API HỆ THỐNG TELEHEALTH HOSPITAL MANAGEMENT (v1.0)

Hệ thống cung cấp các API thông qua API Gateway (prefix `/api/v1`).
Mỗi request cần kèm header:
- `Authorization: Bearer <jwt-token>` (ngoại trừ các endpoint công khai như login/register).
- `Idempotency-Key: <uuid>` (đối với các thao tác đặt lịch hoặc thực hiện thanh toán).

---

## 1. API Gateway
- **Cổng vào:** `http://localhost:8080`
- **Chức năng:** Định tuyến request tới các downstream microservices, xác thực JWT token và chuyển tiếp thông tin định danh (`X-User-Id`, `X-User-Role`, `X-User-Email`) qua HTTP header nội bộ.

---

## 2. Identity Service (Port nội bộ: 8081)

### 2.1 Đăng ký tài khoản (Bệnh nhân)
- **POST** `/api/v1/auth/register`
- **Quyền:** Public
- **Request Body:**
```json
{
  "email": "patient1@demo.com",
  "password": "Password123!",
  "displayName": "Nguyễn Văn A"
}
```
- **Response (201 Created):**
```json
{
  "id": "uuid",
  "email": "patient1@demo.com",
  "displayName": "Nguyễn Văn A",
  "role": "PATIENT"
}
```

### 2.2 Đăng nhập
- **POST** `/api/v1/auth/login`
- **Quyền:** Public
- **Request Body:**
```json
{
  "email": "patient1@demo.com",
  "password": "Password123!"
}
```
- **Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "uuid-or-token",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": "uuid",
    "email": "patient1@demo.com",
    "displayName": "Nguyễn Văn A",
    "role": "PATIENT"
  }
}
```

### 2.3 Lấy thông tin tài khoản hiện tại
- **GET** `/api/v1/auth/me`
- **Quyền:** Authenticated User

### 2.4 Quản trị viên quản lý tài khoản Bác sĩ
- **POST** `/api/v1/admin/users/doctor`
- **Quyền:** `ADMIN`
- **Request Body:**
```json
{
  "email": "doctor.le@demo.com",
  "password": "DoctorPassword123!",
  "displayName": "BS.CKII Lê Văn B",
  "specialtyId": "uuid-specialty",
  "bio": "Chuyên gia Tim mạch 15 năm kinh nghiệm"
}
```

---

## 3. Appointment Service (Port nội bộ: 8082)

### 3.1 Danh sách Chuyên khoa & Bác sĩ
- **GET** `/api/v1/specialties` (Public)
- **POST** `/api/v1/admin/specialties` (ADMIN)
- **GET** `/api/v1/doctors` (Public)
- **GET** `/api/v1/doctors/{id}` (Public)
- **GET** `/api/v1/doctors/{id}/slots?date=YYYY-MM-DD` (Public, lấy các ca khám còn trống)
- **POST** `/api/v1/admin/slots` (ADMIN, tạo ca khám cho bác sĩ)
```json
{
  "doctorId": "uuid",
  "startsAt": "2026-10-20T09:00:00Z"
}
```

### 3.2 Đặt lịch khám (Giữ ca 7 phút)
- **POST** `/api/v1/appointments`
- **Quyền:** `PATIENT`
- **Headers:** `Idempotency-Key: <uuid>`
- **Request Body:**
```json
{
  "doctorId": "uuid-doctor",
  "slotId": "uuid-slot",
  "intakeId": "uuid-intake-snapshot"
}
```
- **Response (201 Created):**
```json
{
  "appointmentId": "uuid",
  "doctorId": "uuid-doctor",
  "doctorName": "BS.CKII Lê Văn B",
  "slotId": "uuid-slot",
  "startsAt": "2026-10-20T09:00:00Z",
  "status": "PENDING_PAYMENT",
  "amountVnd": 100000,
  "createdAt": "2026-10-15T08:00:00Z",
  "expiresAt": "2026-10-15T08:07:00Z",
  "remainingSeconds": 420,
  "paymentCode": "PAY-9F3A1...",
  "qrPayload": "telehealth://pay?code=PAY-9F3A1..."
}
```

### 3.3 Danh sách và Chi tiết lịch khám
- **GET** `/api/v1/appointments` (Lấy danh sách lịch của người dùng hiện tại: bệnh nhân xem lịch của mình, bác sĩ xem ca được phân công)
- **GET** `/api/v1/appointments/{id}` (Chi tiết lịch khám, kiểm tra thời gian hết hạn nếu đang PENDING)

### 3.4 Đổi ca khám (Reschedule)
- **POST** `/api/v1/appointments/{id}/reschedule`
- **Quyền:** `PATIENT` (Chủ lịch)
- **Điều kiện:**
  - Lịch ở trạng thái `CONFIRMED`.
  - Ngày hiện tại theo múi giờ `Asia/Ho_Chi_Minh` phải trước ngày khám (khóa đổi từ 00:00 ngày khám).
  - Ca mới phải cùng `doctorId`, ca mới còn trống và ở ngày sau ngày hiện tại.
- **Request Body:**
```json
{
  "newSlotId": "uuid-new-slot"
}
```
- **Response (200 OK):** Thông tin lịch đã cập nhật ca mới, ca cũ được giải phóng nguyên tử, sự kiện thông báo được gửi qua RabbitMQ.

### 3.5 Bác sĩ bắt đầu ca khám
- **POST** `/api/v1/appointments/{id}/start`
- **Quyền:** `DOCTOR` được phân công.
- **Quy tắc:** Bác sĩ chỉ được phép có tối đa 1 ca ở trạng thái `IN_PROGRESS` tại cùng một thời điểm.

### 3.6 Hoàn tất ca khám
- **POST** `/api/v1/appointments/{id}/complete`
- **Quyền:** `DOCTOR` được phân công.
- **Điều kiện:** Hồ sơ khám đã có báo cáo được công bố (`PUBLISHED`).

---

## 4. Clinical Service (Port nội bộ: 8083)

### 4.1 Điền biểu mẫu trước khám
- **POST** `/api/v1/intakes`
- **Quyền:** `PATIENT`
- **Request Body:**
```json
{
  "reason": "Đau thắt ngực khi gắng sức",
  "symptoms": "Hồi hộp, khó thở, nhịp tim nhanh",
  "medicalHistory": "Tăng huyết áp 3 năm",
  "underlyingConditions": "Không có",
  "symptomStartedAt": "2026-10-10",
  "currentMedications": "Amlodipine 5mg",
  "drugAllergies": "Không rõ"
}
```
- Lưu ý: Toàn bộ thông tin nhạy cảm được mã hóa AES-256-GCM trước khi ghi vào database `clinical_db`.

### 4.2 Lấy thông tin biểu mẫu
- **GET** `/api/v1/intakes/{id}`
- **Quyền:** Bệnh nhân chủ sở hữu hoặc Bác sĩ được phân công khám ca này.

### 4.3 Tải lên tài liệu báo cáo khám
- **POST** `/api/v1/encounters/{appointmentId}/files`
- **Quyền:** `DOCTOR` được phân công.
- **Content-Type:** `multipart/form-data`
- **File:** DOCX, PDF, hoặc ZIP (tổng tối đa 20MB). Lưu trữ mã hóa tại private volume.

### 4.4 Công bố báo cáo khám
- **POST** `/api/v1/encounters/{appointmentId}/publish`
- **Quyền:** `DOCTOR` được phân công.
- Chuyển trạng thái encounter sang `PUBLISHED`, mở quyền cho bệnh nhân tải báo cáo.

### 4.5 Tải file báo cáo khám
- **GET** `/api/v1/reports/{encounterId}/files/{fileId}/download`
- **Quyền:** Bác sĩ được phân công hoặc Bệnh nhân chủ lịch.

---

## 5. Billing Service (Port nội bộ: 8084)

### 5.1 Lấy thông tin thanh toán ca khám
- **GET** `/api/v1/appointments/{appointmentId}/payment`
- **Quyền:** Bệnh nhân chủ lịch.
- Trả về mã thanh toán `payment_code`, số tiền VNĐ, trạng thái và thời gian còn lại.

### 5.2 Nút thanh toán mô phỏng (Demo Mock Payment)
- **POST** `/api/v1/billing/mock-pay`
- **Quyền:** `PATIENT` hoặc Internal Test
- **Request Body:**
```json
{
  "paymentCode": "PAY-9F3A1..."
}
```
- **Xử lý:**
  - Kiểm tra đơn còn hiệu lực trong 7 phút.
  - Cập nhật trạng thái `PAID`, lưu transaction ID mô phỏng.
  - Bắn sự kiện `PaymentSucceeded` qua RabbitMQ để Appointment Service chuyển sang `CONFIRMED`.
  - Idempotent: Gọi lại cùng request không sinh lỗi và không xử lý lặp.

---

## 6. Realtime Service (Port nội bộ: 8085)

### 6.1 Lấy vé vào phòng khám (Admission Token)
- **POST** `/api/v1/rooms/{appointmentId}/token`
- **Quyền:** Bác sĩ hoặc Bệnh nhân của lịch hẹn.
- **Response:**
```json
{
  "roomId": "uuid",
  "token": "signed-room-token",
  "admissionVersion": 1,
  "role": "PATIENT"
}
```

### 6.2 WebSocket & STOMP Signaling
- **Endpoint kết nối WebSocket:** `ws://localhost:8080/ws` (hoặc `/realtime/ws`)
- **Kênh Chat:** `/topic/room.{roomId}`
  - Gửi tin nhắn qua STOMP: `/app/room.{roomId}.send`
  - Body: `{"clientMessageId": "uuid", "body": "Chào bác sĩ"}`
  - Tin nhắn được mã hóa lưu trữ vào `realtime_db`. Reconnecting client tải lại qua API `GET /api/v1/rooms/{roomId}/messages`.
- **Kênh WebRTC Signaling:** `/topic/room.{roomId}.signal`
  - Gửi offer, answer, ice-candidate giữa 2 bên.

### 6.3 Cấu hình STUN/TURN (WebRTC ICE Servers)
- **GET** `/api/v1/webrtc/config`
- Trả về cấu hình ICE Servers (Coturn STUN/TURN credentials).

---

## 7. Notification Service (Port nội bộ: 8086)

### 7.1 Luồng xử lý
- Tiêu thụ sự kiện `AppointmentConfirmed` và `AppointmentRescheduled` từ RabbitMQ.
- Trừ trùng bằng bảng `email_jobs` và `inbox_events`.
- Gửi email xác nhận qua Spring Mail tới Mailpit (`localhost:1025`) ở local hoặc SMTP thật khi cấu hình env.
- Tuyệt đối không chứa thông tin bệnh án, triệu chứng, hoặc thuốc trong nội dung email.
