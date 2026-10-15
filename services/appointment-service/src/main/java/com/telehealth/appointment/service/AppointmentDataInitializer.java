package com.telehealth.appointment.service;

import com.telehealth.appointment.entity.AppointmentEntity;
import com.telehealth.appointment.entity.DoctorEntity;
import com.telehealth.appointment.entity.SlotClaimEntity;
import com.telehealth.appointment.entity.SlotEntity;
import com.telehealth.appointment.entity.SpecialtyEntity;
import com.telehealth.appointment.repository.AppointmentRepository;
import com.telehealth.appointment.repository.DoctorRepository;
import com.telehealth.appointment.repository.SlotClaimRepository;
import com.telehealth.appointment.repository.SlotRepository;
import com.telehealth.appointment.repository.SpecialtyRepository;
import com.telehealth.contracts.enums.AppointmentStatus;
import com.telehealth.contracts.enums.ClaimKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentDataInitializer implements ApplicationRunner {

    private final SpecialtyRepository specialtyRepository;
    private final DoctorRepository doctorRepository;
    private final SlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final SlotClaimRepository slotClaimRepository;

    public static final UUID DOCTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID DOCTOR_2_ID = UUID.fromString("22222222-2222-2222-2222-222222222223");
    public static final UUID PATIENT_1_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID SPECIALTY_TIM_MACH_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID SPECIALTY_NHI_KHOA_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    public static final UUID SPECIALTY_NOI_KHOA_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    /**
     * Danh mục mặc định dùng tên thân thiện với bệnh nhân. Ba UUID đầu tiên được
     * giữ nguyên để không làm mất liên kết của dữ liệu demo hiện có; các UUID còn
     * lại được sinh ổn định từ tên chuyên khoa nên seed có thể chạy nhiều lần.
     */
    private static final List<SpecialtySeed> DEFAULT_SPECIALTIES = List.of(
            new SpecialtySeed(SPECIALTY_TIM_MACH_ID, "Tim mạch"),
            new SpecialtySeed(SPECIALTY_NHI_KHOA_ID, "Nhi khoa"),
            new SpecialtySeed(SPECIALTY_NOI_KHOA_ID, "Nội tổng quát"),
            specialty("Hồi sức cấp cứu"),
            specialty("Tiêu hóa - Gan mật"),
            specialty("Cơ xương khớp"),
            specialty("Thận - Tiết niệu"),
            specialty("Hô hấp"),
            specialty("Dị ứng - Miễn dịch lâm sàng"),
            specialty("Huyết học"),
            specialty("Truyền nhiễm"),
            specialty("Thần kinh"),
            specialty("Da liễu"),
            specialty("Tâm thần"),
            specialty("Nội tiết"),
            specialty("Lão khoa"),
            specialty("Y học gia đình"),
            specialty("Y học cổ truyền"),
            specialty("Gây mê hồi sức"),
            specialty("Ngoại tổng quát"),
            specialty("Ngoại thần kinh"),
            specialty("Ngoại lồng ngực"),
            specialty("Ngoại tiêu hóa"),
            specialty("Ngoại tiết niệu"),
            specialty("Chấn thương chỉnh hình"),
            specialty("Bỏng"),
            specialty("Ung bướu"),
            specialty("Sản phụ khoa"),
            specialty("Nam khoa"),
            specialty("Mắt"),
            specialty("Tai Mũi Họng"),
            specialty("Răng Hàm Mặt"),
            specialty("Sơ sinh"),
            specialty("Phục hồi chức năng"),
            specialty("Dinh dưỡng"),
            specialty("Tâm lý lâm sàng"),
            specialty("Chẩn đoán hình ảnh"),
            specialty("Y học hạt nhân"),
            specialty("Nội soi chẩn đoán và can thiệp"),
            specialty("Thăm dò chức năng"),
            specialty("Huyết học - Truyền máu"),
            specialty("Hóa sinh"),
            specialty("Vi sinh"),
            specialty("Giải phẫu bệnh"),
            specialty("Vi phẫu"),
            specialty("Phẫu thuật nội soi"),
            specialty("Phẫu thuật tạo hình - thẩm mỹ"),
            specialty("Y học dự phòng"),
            specialty("Y học thể thao")
    );

    public static final UUID SAMPLE_APPT_1_ID = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
    public static final UUID SAMPLE_APPT_2_ID = UUID.fromString("aaaaaaaa-2222-2222-2222-222222222222");

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    public void run(ApplicationArguments args) {
        DEFAULT_SPECIALTIES.forEach(specialty -> seedSpecialty(specialty.id(), specialty.name()));

        seedDoctors();
        seedSlots();
        seedSampleAppointments();
    }

    private void seedSpecialty(UUID id, String name) {
        if (specialtyRepository.existsById(id)) {
            return;
        }

        // Admin may already have created this specialty with a different UUID.
        // Preserve that record (including its active state) instead of violating
        // the unique name constraint or overwriting user-managed data.
        if (specialtyRepository.findByNameIgnoreCase(name).isPresent()) {
            log.debug("Specialty already exists with another id, keeping existing data: {}", name);
            return;
        }

        SpecialtyEntity specialty = SpecialtyEntity.builder()
                .id(id)
                .name(name)
                .active(true)
                .build();
        specialtyRepository.save(specialty);
        log.info("Seeded specialty: {}", name);
    }

    private static SpecialtySeed specialty(String name) {
        UUID id = UUID.nameUUIDFromBytes(("telehealth-specialty:" + name).getBytes(StandardCharsets.UTF_8));
        return new SpecialtySeed(id, name);
    }

    private record SpecialtySeed(UUID id, String name) {
    }

    private void seedDoctors() {
        doctorRepository.findById(DOCTOR_ID).ifPresentOrElse(doctor -> {
            doctor.setUserId(DOCTOR_ID);
            doctor.setSpecialtyId(SPECIALTY_TIM_MACH_ID);
            doctor.setDisplayName("BS.CKII Nguyễn Thị Ánh");
            doctor.setBio("Chuyên gia Tim mạch với hơn 15 năm kinh nghiệm khám và tư vấn trực tuyến.");
            doctorRepository.save(doctor);
            log.info("Synchronized demo doctor profile: {}", doctor.getDisplayName());
        }, () -> {
            DoctorEntity doctor = DoctorEntity.builder()
                    .id(DOCTOR_ID)
                    .userId(DOCTOR_ID)
                    .specialtyId(SPECIALTY_TIM_MACH_ID)
                    .displayName("BS.CKII Nguyễn Thị Ánh")
                    .bio("Chuyên gia Tim mạch với hơn 15 năm kinh nghiệm khám và tư vấn trực tuyến.")
                    .active(true)
                    .build();
            doctorRepository.save(doctor);
            log.info("Seeded demo doctor profile: {}", doctor.getDisplayName());
        });

        doctorRepository.findById(DOCTOR_2_ID).ifPresentOrElse(doctor -> {
            doctor.setUserId(DOCTOR_2_ID);
            doctor.setSpecialtyId(SPECIALTY_NHI_KHOA_ID);
            doctor.setDisplayName("BS.CKI Trần Văn Bình");
            doctor.setBio("Chuyên gia Nhi khoa & Chăm sóc sức khỏe toàn diện cho trẻ với hơn 10 năm kinh nghiệm.");
            doctorRepository.save(doctor);
            log.info("Synchronized demo doctor profile: {}", doctor.getDisplayName());
        }, () -> {
            DoctorEntity doctor2 = DoctorEntity.builder()
                    .id(DOCTOR_2_ID)
                    .userId(DOCTOR_2_ID)
                    .specialtyId(SPECIALTY_NHI_KHOA_ID)
                    .displayName("BS.CKI Trần Văn Bình")
                    .bio("Chuyên gia Nhi khoa & Chăm sóc sức khỏe toàn diện cho trẻ với hơn 10 năm kinh nghiệm.")
                    .active(true)
                    .build();
            doctorRepository.save(doctor2);
            log.info("Seeded demo doctor profile: {}", doctor2.getDisplayName());
        });
    }

    private void seedSlots() {
        LocalDate todayVn = LocalDate.now(VN_ZONE);
        LocalTime[] times = {
                LocalTime.of(8, 0), LocalTime.of(8, 30),
                LocalTime.of(9, 0), LocalTime.of(9, 30),
                LocalTime.of(10, 0), LocalTime.of(10, 30),
                LocalTime.of(14, 0), LocalTime.of(14, 30),
                LocalTime.of(15, 0), LocalTime.of(15, 30)
        };

        UUID[] docs = {DOCTOR_ID, DOCTOR_2_ID};
        for (UUID docId : docs) {
            for (int dayOffset = 0; dayOffset <= 4; dayOffset++) {
                LocalDate slotDate = todayVn.plusDays(dayOffset);
                for (LocalTime time : times) {
                    var startsAt = slotDate.atTime(time).atZone(VN_ZONE).toInstant();
                    if (!slotRepository.existsByDoctorIdAndStartsAt(docId, startsAt)) {
                        SlotEntity slot = SlotEntity.builder()
                                .id(UUID.randomUUID())
                                .doctorId(docId)
                                .startsAt(startsAt)
                                .enabled(true)
                                .build();
                        slotRepository.save(slot);
                    }
                }
            }
        }
        log.info("Seeded appointment slots for doctors");
    }

    private void seedSampleAppointments() {
        LocalDate tomorrowVn = LocalDate.now(VN_ZONE).plusDays(1);
        Instant tomorrow9am = tomorrowVn.atTime(9, 0).atZone(VN_ZONE).toInstant();

        // 1. Seed Sample Confirmed Appointment for Patient 1
        if (appointmentRepository.countStoredById(SAMPLE_APPT_1_ID) == 0) {
            SlotEntity slot1 = slotRepository.findAll().stream()
                    .filter(s -> s.getDoctorId().equals(DOCTOR_ID) && s.getStartsAt().equals(tomorrow9am))
                    .findFirst()
                    .orElseGet(() -> slotRepository.save(SlotEntity.builder()
                            .id(UUID.randomUUID())
                            .doctorId(DOCTOR_ID)
                            .startsAt(tomorrow9am)
                            .enabled(true)
                            .build()));

            AppointmentEntity appt1 = AppointmentEntity.builder()
                    .id(SAMPLE_APPT_1_ID)
                    .demo(true)
                    .patientId(PATIENT_1_ID)
                    .doctorId(DOCTOR_ID)
                    .slotId(slot1.getId())
                    .intakeId(SAMPLE_APPT_1_ID)
                    .status(AppointmentStatus.CONFIRMED)
                    .amountVnd(100000L)
                    .currency("VND")
                    .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                    .expiresAt(Instant.now().plus(7, ChronoUnit.MINUTES))
                    .confirmedAt(Instant.now().minus(50, ChronoUnit.MINUTES))
                    .version(1)
                    .build();
            appointmentRepository.save(appt1);

            slotClaimRepository.save(SlotClaimEntity.builder()
                    .slotId(slot1.getId())
                    .appointmentId(appt1.getId())
                    .kind(ClaimKind.BOOKED)
                    .build());
            log.info("Seeded demo confirmed appointment: {}", SAMPLE_APPT_1_ID);
        }

        // 2. Seed Sample Completed Appointment for Patient 1 (Yesterday)
        if (appointmentRepository.countStoredById(SAMPLE_APPT_2_ID) == 0) {
            LocalDate yesterdayVn = LocalDate.now(VN_ZONE).minusDays(1);
            Instant yesterday2pm = yesterdayVn.atTime(14, 0).atZone(VN_ZONE).toInstant();

            SlotEntity slot2 = slotRepository.findAll().stream()
                    .filter(s -> s.getDoctorId().equals(DOCTOR_ID) && s.getStartsAt().equals(yesterday2pm))
                    .findFirst()
                    .orElseGet(() -> slotRepository.save(SlotEntity.builder()
                            .id(UUID.randomUUID())
                            .doctorId(DOCTOR_ID)
                            .startsAt(yesterday2pm)
                            .enabled(true)
                            .build()));

            AppointmentEntity appt2 = AppointmentEntity.builder()
                    .id(SAMPLE_APPT_2_ID)
                    .demo(true)
                    .patientId(PATIENT_1_ID)
                    .doctorId(DOCTOR_ID)
                    .slotId(slot2.getId())
                    .intakeId(SAMPLE_APPT_2_ID)
                    .status(AppointmentStatus.COMPLETED)
                    .amountVnd(100000L)
                    .currency("VND")
                    .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS).plus(7, ChronoUnit.MINUTES))
                    .confirmedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .startedAt(yesterday2pm)
                    .completedAt(yesterday2pm.plus(30, ChronoUnit.MINUTES))
                    .version(2)
                    .build();
            appointmentRepository.save(appt2);

            slotClaimRepository.save(SlotClaimEntity.builder()
                    .slotId(slot2.getId())
                    .appointmentId(appt2.getId())
                    .kind(ClaimKind.BOOKED)
                    .build());
            log.info("Seeded demo completed appointment: {}", SAMPLE_APPT_2_ID);
        }
    }
}
