# TÀI LIỆU YÊU CẦU DỰ ÁN TELEHEALTH

**Phiên bản:** 1.0 — Đã chốt phạm vi nghiệp vụ  
**Ngày:** 14/10/2026  
**Trạng thái:** Người dùng đã chốt bản 0.2 làm cơ sở xây dựng; chưa triển khai. Các chi tiết còn mở được quyết định trong thiết kế, không bổ sung phạm vi nếu chưa thống nhất.

## 1. Mục tiêu và phạm vi

Xây dựng hệ thống đặt lịch và khám trực tuyến ngoại trú giữa bệnh nhân và bác sĩ. Luồng chính gồm điền thông tin trước khám, đặt lịch, thanh toán, khám qua chat/video và nhận báo cáo Word do bác sĩ tải lên.

Tài liệu này tổng hợp yêu cầu đã trao đổi với người dùng. Các nội dung ghi “đề xuất” cần được duyệt; yêu cầu trong đề tài gốc chưa được trao đổi không tự động được coi là đã chốt.

## 2. Vai trò

| Vai trò | Chức năng |
|---|---|
| Bệnh nhân | Chọn bác sĩ và ca khám, điền biểu mẫu, thanh toán, đổi lịch theo điều kiện, tham gia phòng khám, xem lịch sử và tải báo cáo |
| Bác sĩ | Xem lịch khám và biểu mẫu của ca được phân công, tham gia phòng khám, tải báo cáo Word, hoàn tất ca khám |
| Admin — đề xuất | Quản lý tài khoản, hồ sơ bác sĩ, chuyên khoa và lịch làm việc; xem thông tin vận hành và audit log |

Chức năng và quyền chi tiết của Admin chưa được chốt. Chưa đưa vai trò y tá vào phiên bản đầu.

## 3. Luồng đặt lịch và thanh toán

1. Bệnh nhân điền biểu mẫu trước khám.
2. Bệnh nhân chọn bác sĩ, sau đó chọn ca khám còn trống.
3. Khi chọn ca thành công, server tạo lịch `PENDING_PAYMENT`, giữ ca và cấp thời hạn 7 phút; hệ thống hiển thị mã/thông tin thanh toán ngay ở bước này. Không có bước xác nhận giữ chỗ riêng sau đó.
4. Giao diện hiển thị thời gian còn lại theo thời hạn server cấp. Tải lại trang không bắt đầu lại 7 phút.
6. Thanh toán thành công trong thời hạn: lịch chuyển thành `CONFIRMED`, hệ thống gửi email xác nhận.
7. Hết 7 phút chưa thanh toán thành công: lịch chuyển thành `EXPIRED`, ca được giải phóng.

**Đã thống nhất:** Điền biểu mẫu trước khi chọn bác sĩ và ca. Nếu ca vừa bị người khác giữ khi chọn, bệnh nhân chọn ca khác và giữ lại nội dung biểu mẫu.

### Quy tắc

- Một ca chỉ được giữ hoặc xác nhận cho một bệnh nhân tại một thời điểm.
- Server là nơi quyết định ca còn trống và thời hạn giữ chỗ.
- Bệnh nhân không có chức năng chủ động hủy lịch.
- Lịch pending tự hết hạn khác với bệnh nhân hủy lịch đã xác nhận.
- Xử lý thanh toán đến muộn sau khi ca hết hạn nằm ngoài phạm vi phiên bản đầu. Demo cần dùng kịch bản thanh toán trong thời hạn hoặc hết hạn không thanh toán.
- Không xác nhận lịch hoặc gửi email xác nhận nhiều lần khi nhận lại cùng kết quả thanh toán.
- Mức phí dùng giá minh họa, ưu tiên chứng minh luồng hoạt động. Đề xuất mặc định 100.000 VNĐ/lần khám. Cổng thanh toán và cách xác nhận giao dịch (sandbox hay thanh toán thật) chưa chốt.

## 4. Biểu mẫu trước khám

| Trường | Mức yêu cầu |
|---|---|
| Lý do khám | Có trong phạm vi; đề xuất bắt buộc |
| Triệu chứng hiện tại | Có trong phạm vi; đề xuất bắt buộc |
| Tiền sử bệnh | Có trong phạm vi; cho phép ghi không có hoặc không rõ |
| Bệnh nền | Có trong phạm vi; cho phép ghi không có hoặc không rõ |
| Thời điểm bắt đầu triệu chứng | Đề xuất bổ sung |
| Thuốc đang sử dụng | Đề xuất bổ sung |
| Dị ứng thuốc | Đề xuất bổ sung |

Biểu mẫu gắn với lần khám. Bác sĩ của lịch hẹn được xem trước buổi khám. Quyền sửa biểu mẫu sau thanh toán chưa được chốt.

## 5. Đổi lịch

### Đã thống nhất

- Bệnh nhân được đổi lịch sang ca còn trống, không được hủy lịch.
- Chỉ được đổi ở ngày trước ngày khám; vào ngày khám khóa đổi lịch.
- Ví dụ lịch khám ngày 20/10: được đổi đến hết ngày 19/10; từ 00:00 ngày 20/10 không được đổi dù ca khám ở buổi chiều.
- Sau khi đổi lịch thành công, hệ thống gửi email xác nhận cho bệnh nhân.
- Chỉ đổi ca của cùng bác sĩ, không được đổi bác sĩ.
- Không thu phí và không giới hạn số lần đổi, nhưng vẫn phải đáp ứng điều kiện trước ngày khám và ca mới còn trống.

### Quy tắc đề xuất để duyệt

- Tính ngày theo múi giờ `Asia/Ho_Chi_Minh` (UTC+7).
- Chỉ đổi lịch đã thanh toán và ở trạng thái `CONFIRMED`.
- Ca mới ở ngày sau ngày hiện tại.
- Giữ nguyên thanh toán và biểu mẫu trước khám.
- Chỉ giải phóng ca cũ sau khi giữ được ca mới và đổi lịch thành công. Nếu ca mới vừa bị người khác đặt, lịch cũ vẫn có hiệu lực.
- Lưu lịch sử thời gian cũ, thời gian mới và thời điểm đổi.
- Server kiểm tra điều kiện; vô hiệu hóa nút trên giao diện không thay thế kiểm tra server.

## 6. Phòng khám trực tuyến

Mỗi lịch hẹn có một phòng khám dành cho đúng bác sĩ và bệnh nhân của lịch đó.

### Chức năng đã thống nhất

- Chat văn bản và lưu lịch sử.
- Gọi video 1–1.
- Bật/tắt camera, bật/tắt microphone.
- Tắt camera vẫn tiếp tục gọi thoại.
- Hiển thị người còn lại đã vào phòng chưa.
- Kết thúc cuộc gọi và vào lại khi mất kết nối.
- Không làm gọi nhóm hoặc ghi hình trong phiên bản đầu.
- Không giới hạn thời lượng buổi khám và không tự ngắt cuộc gọi sau 15 phút hoặc thời lượng cố định khác. Ca đặt lịch vẫn có ngày/giờ bắt đầu; cách bố trí các giờ bắt đầu và xử lý khi buổi khám kéo dài sang lịch tiếp theo cần thiết kế riêng.

### Quy tắc đề xuất

- Chỉ lịch đã xác nhận mới được vào phòng; kiểm tra quyền trên server.
- Người biết mã phòng nhưng không thuộc lịch hẹn không được vào.
- Kết thúc cuộc gọi không tự động hoàn tất ca khám; bác sĩ còn cần gửi báo cáo.
- Có trạng thái chờ, đang kết nối, đang gọi, mất kết nối và kết thúc trên giao diện.
- Lưu tin nhắn; không lưu âm thanh/video.
- Thời điểm được vào phòng trước giờ hẹn và hạn vào lại phòng cần chốt.

Triển khai video trên HTTPS/WSS, có signaling và STUN/TURN. Kiểm thử giữa hai thiết bị ở hai mạng khác nhau, gồm trường hợp truyền qua TURN.

## 7. Báo cáo khám

### Đã thống nhất

- Bác sĩ gửi báo cáo bằng cách tải lên file hoặc thư mục tài liệu; bệnh nhân tải xuống. File Word là một dạng tài liệu minh họa, không phải định dạng duy nhất.
- Nội dung file thể hiện kết luận bệnh và hướng dẫn dùng thuốc.
- Không xây form nhập chẩn đoán hoặc kê đơn có cấu trúc trong phiên bản đầu.
- Bệnh nhân tải báo cáo của lần khám từ hệ thống.

### Quy tắc đề xuất

- Đề xuất hỗ trợ một hoặc nhiều file; với thư mục, tải cả thư mục hoặc đóng gói thành `.zip` để gửi và tải xuống. Danh sách định dạng và giới hạn dung lượng cần chốt.
- Bác sĩ chỉ tải báo cáo cho ca được phân công.
- Ca khám chỉ chuyển sang `COMPLETED` sau khi có báo cáo.
- Bệnh nhân được tải báo cáo khi bác sĩ hoàn tất ca khám.
- Lưu tên file, vị trí lưu trữ nội bộ, người tải lên và thời điểm tải lên.
- File lưu riêng tư, tải qua kiểm tra quyền; không dùng liên kết công khai cố định.
- Quy tắc thay thế file và sửa báo cáo sau khi hoàn tất chưa được chốt.

Hệ thống quản lý file đính kèm, không phân tích nội dung tài liệu. Chưa hỗ trợ thống kê theo bệnh hoặc thuốc trong báo cáo. Chưa có đơn thuốc điện tử dạng dữ liệu riêng, quản lý kho hoặc cấp phát thuốc.

## 8. Email thông báo

| Sự kiện | Nội dung |
|---|---|
| Thanh toán thành công và lịch được xác nhận | Mã lịch hẹn, bác sĩ, ngày giờ khám, xác nhận thanh toán, liên kết trang lịch hẹn |
| Đổi lịch thành công | Mã lịch hẹn, bác sĩ, thời gian cũ và mới, liên kết trang lịch hẹn |

Hai sự kiện gửi email trên đã được thống nhất. Nội dung chi tiết và cách xử lý lỗi dưới đây là đề xuất:

- Gửi sau khi cập nhật nghiệp vụ thành công.
- Không đưa triệu chứng, bệnh án hoặc nội dung dùng thuốc vào email.
- Email thất bại không làm mất hiệu lực thanh toán hay đổi lịch; hệ thống ghi nhận để gửi lại.
- Có cơ chế tránh tạo nhiều yêu cầu gửi cho cùng một sự kiện.
- Chưa yêu cầu email nhắc lịch, email có báo cáo hoặc SMS.

## 9. Trạng thái lịch hẹn — đề xuất

| Trạng thái | Ý nghĩa |
|---|---|
| `PENDING_PAYMENT` | Giữ ca, chờ thanh toán trong 7 phút |
| `EXPIRED` | Hết hạn thanh toán, đã giải phóng ca |
| `CONFIRMED` | Đã thanh toán, lịch có hiệu lực |
| `IN_PROGRESS` | Bác sĩ bắt đầu ca khám |
| `COMPLETED` | Có báo cáo và bác sĩ hoàn tất ca khám |

Đổi lịch cập nhật ca của lịch `CONFIRMED`, không tạo trạng thái hủy. Trạng thái kết nối cuộc gọi được quản lý riêng với trạng thái lịch hẹn.

## 10. Bảo mật và yêu cầu kỹ thuật

Các yêu cầu này giữ định hướng của đề tài gốc và cần duyệt mức triển khai cụ thể:

- Đăng nhập, phân quyền và kiểm tra quyền với từng lịch hẹn, biểu mẫu, phòng khám, báo cáo.
- Bệnh nhân chỉ truy cập dữ liệu của mình; bác sĩ truy cập dữ liệu phục vụ ca được phân công.
- Mã hóa dữ liệu y tế nhạy cảm khi lưu và bảo vệ khóa mã hóa.
- Ghi audit log thao tác xem/sửa dữ liệu khám, tải báo cáo và đổi lịch; không ghi nội dung nhạy cảm hoặc bí mật vào log thông thường.
- Bảo vệ file báo cáo và thông tin truy cập database.
- Không đưa secret vào repository.
- Bảo đảm chống đặt trùng bằng transaction/ràng buộc database; Redis hỗ trợ giữ chỗ và cache theo thiết kế được duyệt.
- Các thông báo thanh toán và sự kiện được xử lý lặp an toàn.
- Chưa tuyên bố hệ thống đạt chứng nhận hoặc tuân thủ đầy đủ HIPAA/GDPR.

Microservices là định hướng trong đề tài gốc. Đã chốt triển khai trên VPS hiện có của người dùng, dùng Docker và Docker Compose để quản lý các container. Chưa kiểm tra tài nguyên, phiên bản hệ điều hành, Docker, tên miền hoặc cấu hình mạng thực tế của VPS. Stack, số service, broker, database và cách chia service chưa chốt. Việc đơn giản hóa đơn thuốc cần đối chiếu tiêu chí của giảng viên.

## 11. Ngoài phạm vi phiên bản đầu

- Xét nghiệm, siêu âm, chuyển tuyến, nhập viện và điều trị nội trú.
- Xử lý bác sĩ hoặc bệnh nhân không xuất hiện.
- Xử lý thanh toán đến muộn sau hết hạn.
- Bệnh nhân chủ động hủy lịch và quy trình hoàn tiền do hủy.
- Bảo hiểm, kho thuốc, cấp phát thuốc và đơn thuốc có cấu trúc.
- Ghi hình, gọi nhóm, chia sẻ màn hình.
- AI chẩn đoán hoặc phân tích báo cáo Word.

## 12. Tiêu chí nghiệm thu bản đầu

1. Hai bệnh nhân cùng đặt một ca: chỉ một người giữ được ca.
2. Thời hạn pending là 7 phút, tải lại trang không kéo dài thời hạn; hết hạn giải phóng ca.
3. Thanh toán thành công xác nhận lịch và tạo yêu cầu gửi email.
4. Đổi lịch được vào ngày trước ngày khám, bị chặn từ 00:00 ngày khám ở cả API và giao diện.
5. Đổi sang ca vừa bị chiếm không làm mất lịch cũ; đổi thành công gửi email có giờ cũ và mới.
6. Bác sĩ xem được biểu mẫu của ca được phân công; người không có quyền bị từ chối.
7. Hai người chat, xem lại lịch sử và gọi video trên hai mạng khác nhau; bật/tắt thiết bị hoạt động.
8. Người bị ngắt mạng có thể vào lại phòng khi ca còn cho phép; người không thuộc ca không vào được.
9. Bác sĩ tải báo cáo Word, hoàn tất ca; bệnh nhân tải được đúng báo cáo của mình.
10. Email gửi lỗi không làm đảo ngược kết quả đặt hoặc đổi lịch.
11. Chứng minh mã hóa dữ liệu nhạy cảm và audit log theo mức triển khai đã duyệt.

## 13. Các điểm cần chốt trước khi xây dựng

| Điểm cần duyệt | Đề xuất hiện tại |
|---|---|
| Thứ tự điền form và giữ ca — đã chốt | Điền form → chọn bác sĩ → chọn ca thành công → pending 7 phút và hiện mã thanh toán |
| Điều kiện đổi lịch — đã chốt một phần | Không đổi bác sĩ, miễn phí, không giới hạn số lần; khóa từ ngày khám. Đề xuất ca mới sau ngày hiện tại |
| Thời lượng ca khám — đã chốt | Không giới hạn thời lượng cuộc khám; cần thiết kế cách bố trí giờ bắt đầu các ca |
| Mức phí và cổng thanh toán | Giá minh họa tùy chọn; đề xuất 100.000 VNĐ/lần. Cách xác nhận thanh toán chưa chốt |
| Giờ mở phòng và hạn vào lại | Chưa chốt |
| Biểu mẫu | Duyệt trường bổ sung, trường bắt buộc và quyền sửa |
| Báo cáo | Đã chốt gửi file hoặc thư mục để tải xuống; duyệt cách đóng gói thư mục, định dạng, dung lượng và quyền sửa |
| Quản trị | Duyệt phạm vi Admin và cách tạo lịch làm việc |
| Kiến trúc | Duyệt yêu cầu microservices và điều chỉnh Pharmacy theo yêu cầu giảng viên |
| Hạ tầng — đã chốt | VPS hiện có + Docker + Docker Compose; cần khảo sát cấu hình trước triển khai |
| Thông tin lập kế hoạch, không phải chức năng | Số người thực hiện, thời hạn hoàn thành và công nghệ sử dụng (stack); có thể quyết định sau |

Sau khi duyệt, cập nhật tài liệu thành bản 1.0 và dùng làm cơ sở thiết kế màn hình, API, dữ liệu và kiểm thử.
