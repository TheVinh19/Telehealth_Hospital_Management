package com.telehealth.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class EmailRenderer {

    private final ObjectMapper objectMapper;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm - 'Ngày' dd/MM/yyyy")
            .withZone(VN_ZONE);

    public record RenderedEmail(String subject, String htmlContent) {}

    public RenderedEmail renderConfirmed(String payloadJson) {
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            String appointmentId = root.has("appointmentId") ? root.get("appointmentId").asText() : "";
            String code = appointmentId.length() >= 8 ? appointmentId.substring(0, 8).toUpperCase() : appointmentId;
            String doctorName = root.has("doctorName") ? root.get("doctorName").asText() : "Bác sĩ phụ trách";
            String startsAtStr = root.has("startsAt") ? root.get("startsAt").asText() : "";
            String timeFormatted = formatTime(startsAtStr);
            String amount = root.hasNonNull("amountVnd")
                    ? NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(root.get("amountVnd").asLong()) + " VNĐ"
                    : "Đã thanh toán";

            String subject = "[Telehealth] Xác nhận đặt lịch khám thành công - Mã #" + code;
            String html = """
                <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; padding: 24px;">
                    <div style="text-align: center; border-bottom: 2px solid #2563eb; padding-bottom: 16px; margin-bottom: 20px;">
                        <h2 style="color: #2563eb; margin: 0;">BỆNH VIỆN TRỰC TUYẾN TELEHEALTH</h2>
                        <p style="color: #64748b; margin: 4px 0 0 0; font-size: 14px;">Hệ thống Khám và Tư vấn Sức khỏe Ngoại trú</p>
                    </div>
                    <p>Kính chào Quý người bệnh,</p>
                    <p>Lịch khám của Quý khách đã được xác nhận thanh toán thành công. Dưới đây là thông tin chi tiết:</p>
                    <div style="background-color: #f8fafc; border-left: 4px solid #2563eb; padding: 16px; margin: 20px 0; border-radius: 4px;">
                        <p style="margin: 6px 0;"><strong>Mã lịch hẹn:</strong> #%s</p>
                        <p style="margin: 6px 0;"><strong>Bác sĩ khám:</strong> %s</p>
                        <p style="margin: 6px 0;"><strong>Thời gian bắt đầu:</strong> <span style="color: #1e40af; font-weight: bold;">%s (Giờ Việt Nam)</span></p>
                        <p style="margin: 6px 0;"><strong>Trạng thái:</strong> <span style="color: #16a34a; font-weight: bold;">ĐÃ XÁC NHẬN</span></p>
                        <p style="margin: 6px 0;"><strong>Thanh toán:</strong> %s</p>
                    </div>
                    <p><strong>Hướng dẫn tham gia:</strong></p>
                    <ul>
                        <li>Quý khách vui lòng đăng nhập vào hệ thống trước giờ hẹn 10 phút.</li>
                        <li>Đảm bảo thiết bị có kết nối Internet ổn định, camera và microphone hoạt động tốt.</li>
                    </ul>
                    <div style="margin-top: 24px; padding-top: 16px; border-top: 1px solid #e2e8f0; font-size: 12px; color: #64748b;">
                        <em>* Nhằm bảo đảm an toàn dữ liệu y tế, email chỉ thông báo thời gian ca hẹn và không bao gồm nội dung thăm khám y khoa.</em>
                    </div>
                </div>
                """.formatted(html(code), html(doctorName), html(timeFormatted), amount);

            return new RenderedEmail(subject, html);
        } catch (Exception e) {
            throw new RuntimeException("Failed to render confirmed email", e);
        }
    }

    public RenderedEmail renderRescheduled(String payloadJson) {
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            String appointmentId = root.has("appointmentId") ? root.get("appointmentId").asText() : "";
            String code = appointmentId.length() >= 8 ? appointmentId.substring(0, 8).toUpperCase() : appointmentId;
            String doctorName = root.has("doctorName") ? root.get("doctorName").asText() : "Bác sĩ phụ trách";
            String oldStartsAt = root.has("oldStartsAt") ? formatTime(root.get("oldStartsAt").asText()) : "";
            String newStartsAt = root.has("newStartsAt") ? formatTime(root.get("newStartsAt").asText()) : "";

            String subject = "[Telehealth] Xác nhận đổi ca khám thành công - Mã #" + code;
            String html = """
                <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; padding: 24px;">
                    <div style="text-align: center; border-bottom: 2px solid #2563eb; padding-bottom: 16px; margin-bottom: 20px;">
                        <h2 style="color: #2563eb; margin: 0;">BỆNH VIỆN TRỰC TUYẾN TELEHEALTH</h2>
                        <p style="color: #64748b; margin: 4px 0 0 0; font-size: 14px;">Thông báo cập nhật lịch khám</p>
                    </div>
                    <p>Kính chào Quý người bệnh,</p>
                    <p>Yêu cầu đổi ca khám cho lịch hẹn <strong>#%s</strong> của Quý khách đã được thực hiện thành công:</p>
                    <div style="background-color: #f8fafc; border-left: 4px solid #3b82f6; padding: 16px; margin: 20px 0; border-radius: 4px;">
                        <p style="margin: 6px 0;"><strong>Bác sĩ phụ trách:</strong> %s</p>
                        <p style="margin: 6px 0;"><strong>Thời gian ca khám cũ:</strong> <span style="text-decoration: line-through; color: #ef4444;">%s</span></p>
                        <p style="margin: 6px 0;"><strong>Thời gian ca khám MỚI:</strong> <span style="color: #16a34a; font-weight: bold;">%s (Giờ Việt Nam)</span></p>
                        <p style="margin: 6px 0;"><strong>Phí đổi lịch:</strong> 0 VNĐ (Miễn phí)</p>
                    </div>
                    <p>Thanh toán và biểu mẫu trước khám của Quý khách được giữ nguyên sang ca khám mới.</p>
                </div>
                """.formatted(html(code), html(doctorName), html(oldStartsAt), html(newStartsAt));

            return new RenderedEmail(subject, html);
        } catch (Exception e) {
            throw new RuntimeException("Failed to render rescheduled email", e);
        }
    }

    private String html(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private String formatTime(String isoString) {
        if (isoString == null || isoString.isBlank()) return "Chưa xác định";
        try {
            Instant instant = Instant.parse(isoString);
            return FORMATTER.format(instant);
        } catch (Exception e) {
            return isoString;
        }
    }
}
