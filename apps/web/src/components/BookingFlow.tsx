import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { Specialty, Doctor, Slot, Appointment, IntakeForm } from '../types';
import { CheckCircle2, AlertCircle, Clock, CreditCard, User, Calendar, FileText } from 'lucide-react';
import { addVietnamDays, toVietnamDateInput } from '../utils/date';

interface BookingFlowProps {
  onSuccess: (appointment: Appointment) => void;
}

export const BookingFlow: React.FC<BookingFlowProps> = ({ onSuccess }) => {
  const [step, setStep] = useState<'INTAKE' | 'SLOT' | 'PAYMENT' | 'DONE'>('INTAKE');

  // Intake Form State
  const [intake, setIntake] = useState<IntakeForm>({
    reason: '',
    symptoms: '',
    medicalHistory: '',
    underlyingDiseases: '',
    symptomOnset: '',
    currentMedications: '',
    drugAllergies: ''
  });
  const [intakeId, setIntakeId] = useState<string | null>(null);

  // Selection States
  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [selectedSpecialty, setSelectedSpecialty] = useState<string>('');
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [selectedDoctor, setSelectedDoctor] = useState<string>('');
  const [selectedDate, setSelectedDate] = useState<string>(() => addVietnamDays(1));
  const [slots, setSlots] = useState<Slot[]>([]);
  const [selectedSlot, setSelectedSlot] = useState<Slot | null>(null);
  const [loadingDoctors, setLoadingDoctors] = useState<boolean>(false);
  const [loadingSlots, setLoadingSlots] = useState<boolean>(false);

  // Appointment & Payment State
  const [appointment, setAppointment] = useState<Appointment | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState<number>(420); // 7 minutes
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Load Specialties on Mount
  useEffect(() => {
    api.getSpecialties()
      .then(res => {
        setSpecialties(res);
        if (res.length > 0) setSelectedSpecialty(res[0].id);
      })
      .catch(err => setError(err.message));
  }, []);

  // Load Doctors when Specialty changes
  useEffect(() => {
    let active = true;

    setDoctors([]);
    setSelectedDoctor('');
    setSlots([]);
    setSelectedSlot(null);

    if (!selectedSpecialty) {
      setLoadingDoctors(false);
      return () => {
        active = false;
      };
    }

    setLoadingDoctors(true);
    setError(null);
    api.getDoctors(selectedSpecialty)
      .then(res => {
        if (!active) return;
        setDoctors(res);
        setSelectedDoctor(res[0]?.id || '');
      })
      .catch(err => {
        if (active) setError(err.message);
      })
      .finally(() => {
        if (active) setLoadingDoctors(false);
      });

    return () => {
      active = false;
    };
  }, [selectedSpecialty]);

  // Load Available Slots when Doctor or Date changes
  useEffect(() => {
    let active = true;

    setSlots([]);
    setSelectedSlot(null);

    if (!selectedDoctor || !selectedDate) {
      setLoadingSlots(false);
      return () => {
        active = false;
      };
    }

    setLoadingSlots(true);
    setError(null);
    api.getAvailableSlots(selectedDoctor, selectedDate)
      .then(res => {
        if (active) setSlots(res.filter(slot => slot.available !== false));
      })
      .catch(err => {
        if (active) setError(err.message);
      })
      .finally(() => {
        if (active) setLoadingSlots(false);
      });

    return () => {
      active = false;
    };
  }, [selectedDoctor, selectedDate]);

  // Countdown Timer for Payment Step
  useEffect(() => {
    if (step === 'PAYMENT' && appointment?.paymentExpiresAt) {
      const expiry = new Date(appointment.paymentExpiresAt).getTime();
      const interval = setInterval(() => {
        const now = Date.now();
        const diff = Math.max(0, Math.floor((expiry - now) / 1000));
        setRemainingSeconds(diff);
        if (diff <= 0) {
          clearInterval(interval);
          setError('Ca giữ chỗ đã hết hạn 7 phút. Vui lòng chọn ca khám khác.');
        }
      }, 1000);
      return () => clearInterval(interval);
    }
  }, [step, appointment]);

  // Step 1: Submit Intake Form
  const handleIntakeSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!intake.reason || !intake.symptoms || !intake.medicalHistory || !intake.underlyingDiseases) {
      setError('Vui lòng điền đầy đủ các thông tin y tế bắt buộc.');
      return;
    }

    setLoading(true);
    try {
      const res = await api.submitIntake(intake);
      setIntakeId(res.intakeId);
      setStep('SLOT');
    } catch (err: any) {
      setError(err.message || 'Lỗi gửi biểu mẫu trước khám');
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Hold Selected Slot
  const handleHoldSlot = async () => {
    if (!selectedSlot || !intakeId) {
      setError('Vui lòng chọn một ca khám.');
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const res = await api.holdAppointment(selectedDoctor, selectedSlot.id, intakeId);
      setAppointment(res);
      setStep('PAYMENT');
    } catch (err: any) {
      setError(err.message || 'Ca khám đã được người khác giữ hoặc không khả dụng.');
      // Refresh slots
      if (selectedDoctor && selectedDate) {
        api.getAvailableSlots(selectedDoctor, selectedDate).then(setSlots);
      }
    } finally {
      setLoading(false);
    }
  };

  // Step 3: Mock Pay
  const handleMockPayment = async () => {
    if (!appointment) return;
    setLoading(true);
    setError(null);
    try {
      // First get payment order
      const order = await api.getPaymentOrder(appointment.id);
      const code = order.paymentCode || (appointment as any).paymentCode || `PAY-${appointment.id.substring(0, 8).toUpperCase()}`;
      await api.mockPay(code);
      const updatedAppt = await api.getAppointment(appointment.id);
      setAppointment(updatedAppt);
      setStep('DONE');
    } catch (err: any) {
      setError(err.message || 'Thanh toán thất bại.');
    } finally {
      setLoading(false);
    }
  };

  const formatTimer = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto' }}>
      {/* Progress indicator */}
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '2rem', position: 'relative' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: step === 'INTAKE' ? 700 : 500, color: step === 'INTAKE' ? 'var(--primary)' : 'var(--text-muted)' }}>
          <FileText size={18} /> 1. Biểu mẫu tiền khám
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: step === 'SLOT' ? 700 : 500, color: step === 'SLOT' ? 'var(--primary)' : 'var(--text-muted)' }}>
          <Calendar size={18} /> 2. Chọn bác sĩ & ca
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: step === 'PAYMENT' ? 700 : 500, color: step === 'PAYMENT' ? 'var(--primary)' : 'var(--text-muted)' }}>
          <CreditCard size={18} /> 3. Thanh toán (7 phút)
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: step === 'DONE' ? 700 : 500, color: step === 'DONE' ? 'var(--success)' : 'var(--text-muted)' }}>
          <CheckCircle2 size={18} /> 4. Hoàn tất
        </div>
      </div>

      {error && (
        <div className="alert alert-danger" id="booking-error-alert">
          <AlertCircle size={20} />
          <div>{error}</div>
        </div>
      )}

      {/* STEP 1: INTAKE FORM */}
      {step === 'INTAKE' && (
        <div className="card">
          <div className="card-header">
            <h2 className="card-title">
              <FileText size={20} color="var(--primary)" />
              Biểu mẫu thông tin trước khám
            </h2>
          </div>
          <p style={{ color: 'var(--text-secondary)', marginBottom: '1.25rem', fontSize: '0.9rem' }}>
            Thông tin y tế sẽ được mã hóa chuẩn AES-256-GCM nhằm bảo đảm quyền riêng tư tuyệt đối cho người bệnh.
          </p>

          <form onSubmit={handleIntakeSubmit}>
            <div className="form-group">
              <label className="form-label">
                Lý do khám bệnh <span className="required">*</span>
              </label>
              <input
                id="intake-reason-input"
                type="text"
                placeholder="Ví dụ: Đau đầu dai dẳng, sốt nhẹ về chiều"
                value={intake.reason}
                onChange={e => setIntake({ ...intake, reason: e.target.value })}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">
                Triệu chứng chi tiết <span className="required">*</span>
              </label>
              <textarea
                id="intake-symptoms-input"
                rows={3}
                placeholder="Mô tả mức độ đau, thời điểm xuất hiện, cảm giác kèm theo..."
                value={intake.symptoms}
                onChange={e => setIntake({ ...intake, symptoms: e.target.value })}
                required
              />
            </div>

            <div className="grid-2">
              <div className="form-group">
                <label className="form-label">
                  Tiền sử bệnh bản thân & gia đình <span className="required">*</span>
                </label>
                <input
                  id="intake-history-input"
                  type="text"
                  placeholder="Ví dụ: Không có bệnh mãn tính / Viêm xoang"
                  value={intake.medicalHistory}
                  onChange={e => setIntake({ ...intake, medicalHistory: e.target.value })}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">
                  Bệnh nền đang điều trị <span className="required">*</span>
                </label>
                <input
                  id="intake-diseases-input"
                  type="text"
                  placeholder="Ví dụ: Cao huyết áp nhẹ / Tiểu đường / Không có"
                  value={intake.underlyingDiseases}
                  onChange={e => setIntake({ ...intake, underlyingDiseases: e.target.value })}
                  required
                />
              </div>
            </div>

            <div style={{ marginTop: '0.5rem', marginBottom: '1.25rem', padding: '1rem', background: 'var(--bg-subtle)', borderRadius: 'var(--radius-md)' }}>
              <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.75rem' }}>
                Thông tin bổ sung (Tùy chọn)
              </div>
              <div className="grid-3">
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label className="form-label">Khởi phát triệu chứng</label>
                  <input
                    id="intake-onset-input"
                    type="text"
                    placeholder="Khoảng 3 ngày trước"
                    value={intake.symptomOnset}
                    onChange={e => setIntake({ ...intake, symptomOnset: e.target.value })}
                  />
                </div>
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label className="form-label">Thuốc đang dùng</label>
                  <input
                    id="intake-meds-input"
                    type="text"
                    placeholder="Paracetamol 500mg"
                    value={intake.currentMedications}
                    onChange={e => setIntake({ ...intake, currentMedications: e.target.value })}
                  />
                </div>
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label className="form-label">Dị ứng thuốc</label>
                  <input
                    id="intake-allergies-input"
                    type="text"
                    placeholder="Dị ứng Penicillin"
                    value={intake.drugAllergies}
                    onChange={e => setIntake({ ...intake, drugAllergies: e.target.value })}
                  />
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button
                id="intake-submit-btn"
                type="submit"
                className="btn btn-primary"
                disabled={loading}
              >
                {loading ? 'Đang lưu...' : 'Tiếp tục: Chọn bác sĩ & ca khám'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* STEP 2: SELECT DOCTOR & SLOT */}
      {step === 'SLOT' && (
        <div className="card">
          <div className="card-header">
            <h2 className="card-title">
              <Calendar size={20} color="var(--primary)" />
              Chọn Chuyên khoa, Bác sĩ & Ca khám
            </h2>
            <button
              className="btn btn-secondary"
              style={{ fontSize: '0.85rem', padding: '0.4rem 0.8rem' }}
              onClick={() => setStep('INTAKE')}
            >
              Quay lại biểu mẫu
            </button>
          </div>

          <div className="grid-3" style={{ marginBottom: '1.5rem' }}>
            <div className="form-group">
              <label className="form-label">Chuyên khoa</label>
              <select
                id="select-specialty"
                value={selectedSpecialty}
                onChange={e => {
                  setSelectedSpecialty(e.target.value);
                  setError(null);
                }}
              >
                {specialties.map(spec => (
                  <option key={spec.id} value={spec.id}>{spec.name}</option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Bác sĩ phụ trách</label>
              <select
                id="select-doctor"
                value={selectedDoctor}
                disabled={loadingDoctors || doctors.length === 0}
                onChange={e => {
                  setSelectedDoctor(e.target.value);
                  setError(null);
                }}
              >
                {loadingDoctors && <option value="">Đang tải bác sĩ...</option>}
                {!loadingDoctors && doctors.length === 0 && (
                  <option value="">Không có bác sĩ thuộc chuyên khoa này</option>
                )}
                {doctors.map(doc => (
                  <option key={doc.id} value={doc.id}>
                    {doc.fullName}{doc.experienceYears != null ? ` (${doc.experienceYears} năm KN)` : ''}
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Ngày khám</label>
              <input
                id="select-date"
                type="date"
                value={selectedDate}
                min={toVietnamDateInput()}
                onChange={e => {
                  setSelectedDate(e.target.value);
                  setError(null);
                }}
              />
            </div>
          </div>

          <div style={{ marginBottom: '1.5rem' }}>
            <label className="form-label" style={{ marginBottom: '0.75rem' }}>
              Ca khám còn trống trong ngày ({slots.length} ca khả dụng)
            </label>
            {loadingSlots ? (
              <div className="alert alert-info">
                Đang tải ca khám...
              </div>
            ) : slots.length === 0 ? (
              <div className="alert alert-info">
                Bác sĩ hiện chưa có ca khám khả dụng vào ngày này. Vui lòng chọn ngày khác hoặc bác sĩ khác.
              </div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '0.75rem' }}>
                {slots.map(slot => (
                  <div
                    key={slot.id}
                    id={`slot-card-${slot.id}`}
                    className={`slot-card ${selectedSlot?.id === slot.id ? 'selected' : ''}`}
                    onClick={() => setSelectedSlot(slot)}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontWeight: 700, fontSize: '0.95rem' }}>
                      <Clock size={16} color="var(--primary)" />
                      {slot.startTime.substring(0, 5)} - {slot.endTime.substring(0, 5)}
                    </div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.3rem' }}>
                      Ngày: {slot.slotDate}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button
              id="hold-slot-btn"
              className="btn btn-primary"
              disabled={!selectedSlot || loading}
              onClick={handleHoldSlot}
            >
              {loading ? 'Đang giữ ca...' : 'Giữ ca & Tiến hành thanh toán'}
            </button>
          </div>
        </div>
      )}

      {/* STEP 3: PAYMENT WITH 7-MINUTE COUNTDOWN */}
      {step === 'PAYMENT' && appointment && (
        <div className="card">
          <div className="card-header">
            <h2 className="card-title">
              <CreditCard size={20} color="var(--warning)" />
              Thanh toán giữ ca khám (PENDING_PAYMENT)
            </h2>
            <div className="countdown-pill" id="countdown-timer">
              <Clock size={16} />
              {formatTimer(remainingSeconds)}
            </div>
          </div>

          <div className="alert alert-warning">
            Ca khám đang được tạm giữ trong <strong>7 phút</strong>. Sau thời gian này nếu chưa thanh toán, hệ thống sẽ tự động hủy giữ ca để giải phóng cho bệnh nhân khác.
          </div>

          <div className="grid-2" style={{ marginBottom: '1.5rem' }}>
            <div style={{ background: 'var(--bg-subtle)', padding: '1.25rem', borderRadius: 'var(--radius-md)' }}>
              <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>
                Chi tiết lịch hẹn:
              </div>
              <div style={{ fontWeight: 700, fontSize: '1.1rem', marginBottom: '0.25rem' }}>
                {appointment.doctorName || 'Bác sĩ chuyên khoa'}
              </div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '0.75rem' }}>
                {appointment.specialtyName}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.9rem', marginBottom: '0.4rem' }}>
                <Calendar size={16} color="var(--primary)" /> Ngày: {appointment.slotDate}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.9rem' }}>
                <Clock size={16} color="var(--primary)" /> Khung giờ: {appointment.startTime.substring(0, 5)} - {appointment.endTime.substring(0, 5)}
              </div>
            </div>

            <div style={{ textAlign: 'center', background: 'var(--bg-subtle)', padding: '1.25rem', borderRadius: 'var(--radius-md)' }}>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>
                Số tiền thanh toán:
              </div>
              <div style={{ fontSize: '1.8rem', fontWeight: 800, color: 'var(--primary)', marginBottom: '1rem' }}>
                {appointment.totalFee?.toLocaleString('vi-VN')} VNĐ
              </div>

              {/* QR Mockup visual */}
              <div style={{ width: '130px', height: '130px', margin: '0 auto 1rem', background: '#fff', padding: '8px', borderRadius: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <svg viewBox="0 0 100 100" style={{ width: '100%', height: '100%' }}>
                  <rect width="100" height="100" fill="white"/>
                  <rect x="10" y="10" width="30" height="30" fill="black"/>
                  <rect x="16" y="16" width="18" height="18" fill="white"/>
                  <rect x="20" y="20" width="10" height="10" fill="black"/>
                  <rect x="60" y="10" width="30" height="30" fill="black"/>
                  <rect x="66" y="16" width="18" height="18" fill="white"/>
                  <rect x="70" y="20" width="10" height="10" fill="black"/>
                  <rect x="10" y="60" width="30" height="30" fill="black"/>
                  <rect x="16" y="66" width="18" height="18" fill="white"/>
                  <rect x="20" y="70" width="10" height="10" fill="black"/>
                  <circle cx="50" cy="50" r="10" fill="#0d9488" />
                </svg>
              </div>

              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                Mã giao dịch mô phỏng (Demo Payment Adapter)
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem' }}>
            <button
              id="mock-pay-btn"
              className="btn btn-primary"
              style={{ padding: '0.75rem 1.75rem', fontSize: '1rem' }}
              disabled={loading || remainingSeconds <= 0}
              onClick={handleMockPayment}
            >
              {loading ? 'Đang xác nhận...' : 'Mô phỏng Thanh toán Thành công (Demo)'}
            </button>
          </div>
        </div>
      )}

      {/* STEP 4: SUCCESS CONFIRMATION */}
      {step === 'DONE' && appointment && (
        <div className="card" style={{ textAlign: 'center', padding: '3rem 2rem' }}>
          <div style={{ width: '64px', height: '64px', margin: '0 auto 1.25rem', background: 'rgba(16, 185, 129, 0.15)', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <CheckCircle2 size={36} color="var(--success)" />
          </div>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 800, marginBottom: '0.5rem' }}>
            Đặt lịch khám thành công!
          </h2>
          <p style={{ color: 'var(--text-secondary)', maxWidth: '480px', margin: '0 auto 1.5rem' }}>
            Lịch hẹn của bạn đã được chuyển sang trạng thái <strong>CONFIRMED</strong>. Email xác nhận kèm thông tin chi tiết đã được gửi đến hòm thư của bạn.
          </p>

          <div style={{ display: 'inline-flex', gap: '1rem' }}>
            <button
              id="go-to-appointments-btn"
              className="btn btn-primary"
              onClick={() => onSuccess(appointment)}
            >
              Xem danh sách lịch hẹn của tôi
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
