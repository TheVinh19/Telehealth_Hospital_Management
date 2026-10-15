import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { Appointment, Slot, EncounterSummary } from '../types';
import { Calendar, Clock, Video, FileDown, RefreshCw, AlertCircle, CheckCircle, X } from 'lucide-react';

interface MyAppointmentsProps {
  onEnterRoom: (appointmentId: string) => void;
}

export const MyAppointments: React.FC<MyAppointmentsProps> = ({ onEnterRoom }) => {
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Reschedule Modal State
  const [reschedulingAppt, setReschedulingAppt] = useState<Appointment | null>(null);
  const [rescheduleDate, setRescheduleDate] = useState<string>(() => {
    const d = new Date();
    d.setDate(d.getDate() + 2);
    return d.toISOString().split('T')[0];
  });
  const [availableSlots, setAvailableSlots] = useState<Slot[]>([]);
  const [selectedNewSlot, setSelectedNewSlot] = useState<Slot | null>(null);
  const [rescheduleLoading, setRescheduleLoading] = useState<boolean>(false);

  // Encounter report download state
  const [reportModalAppt, setReportModalAppt] = useState<Appointment | null>(null);
  const [encounterSummary, setEncounterSummary] = useState<EncounterSummary | null>(null);
  const [downloadLoading, setDownloadLoading] = useState<boolean>(false);

  const fetchAppointments = async () => {
    setLoading(true);
    try {
      const data = await api.getMyAppointments();
      setAppointments(data);
    } catch (err: any) {
      setError(err.message || 'Không thể tải danh sách lịch khám');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAppointments();
  }, []);

  // When opening reschedule modal, fetch slots
  useEffect(() => {
    if (reschedulingAppt && rescheduleDate) {
      api.getAvailableSlots(reschedulingAppt.doctorId, rescheduleDate)
        .then(res => setAvailableSlots(res))
        .catch(err => setError(err.message));
    }
  }, [reschedulingAppt, rescheduleDate]);

  // Check if reschedule is allowed based on Vietnam timezone
  const canReschedule = (appt: Appointment) => {
    if (appt.status !== 'CONFIRMED') return false;
    // locked from 00:00 on appointment date
    const todayStr = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());
    return appt.slotDate > todayStr;
  };

  const handleOpenReschedule = (appt: Appointment) => {
    setError(null);
    setSuccessMsg(null);
    setReschedulingAppt(appt);
    setSelectedNewSlot(null);
  };

  const handleConfirmReschedule = async () => {
    if (!reschedulingAppt || !selectedNewSlot) return;
    setRescheduleLoading(true);
    setError(null);
    try {
      await api.rescheduleAppointment(reschedulingAppt.id, selectedNewSlot.id);
      setSuccessMsg('Đổi ca khám thành công! Email xác nhận đã được gửi.');
      setReschedulingAppt(null);
      await fetchAppointments();
    } catch (err: any) {
      setError(err.message || 'Đổi ca thất bại. Lịch cũ vẫn được giữ nguyên.');
    } finally {
      setRescheduleLoading(false);
    }
  };

  // Open report modal for completed appointment
  const handleViewReports = async (appt: Appointment) => {
    setReportModalAppt(appt);
    setDownloadLoading(true);
    try {
      const enc = await api.getEncounter(appt.id);
      setEncounterSummary(enc);
    } catch (err: any) {
      setError(err.message || 'Không thể tải báo cáo khám');
    } finally {
      setDownloadLoading(false);
    }
  };

  const handleDownloadFile = async (fileId: string, fileName: string) => {
    if (!reportModalAppt) return;
    try {
      await api.downloadReportFile(reportModalAppt.id, fileId, fileName);
    } catch (err: any) {
      alert(err.message || 'Tải file thất bại');
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PENDING_PAYMENT':
        return <span className="badge badge-pending">Chờ thanh toán</span>;
      case 'CONFIRMED':
        return <span className="badge badge-confirmed">Đã xác nhận</span>;
      case 'IN_PROGRESS':
        return <span className="badge badge-in-progress">Đang khám</span>;
      case 'COMPLETED':
        return <span className="badge badge-completed">Đã hoàn tất</span>;
      case 'EXPIRED':
        return <span className="badge badge-expired">Đã hết hạn</span>;
      default:
        return <span className="badge badge-cancelled">{status}</span>;
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 800 }}>Lịch khám của tôi</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
            Theo dõi, đổi ca khám hoặc tham gia phòng tư vấn trực tuyến với bác sĩ
          </p>
        </div>
        <button className="btn btn-secondary" onClick={fetchAppointments} disabled={loading}>
          <RefreshCw size={16} className={loading ? 'spin' : ''} />
          Làm mới
        </button>
      </div>

      {error && (
        <div className="alert alert-danger">
          <AlertCircle size={20} />
          <div>{error}</div>
        </div>
      )}

      {successMsg && (
        <div className="alert alert-success">
          <CheckCircle size={20} />
          <div>{successMsg}</div>
        </div>
      )}

      {loading && appointments.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
          Đang tải danh sách lịch khám...
        </div>
      ) : appointments.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
          Bạn chưa có lịch hẹn khám nào. Nhấn "Đặt khám mới" để bắt đầu.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {appointments.map(appt => (
            <div key={appt.id} className="card" id={`appointment-card-${appt.id}`}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.4rem' }}>
                    <span style={{ fontWeight: 700, fontSize: '1.15rem' }}>
                      {appt.doctorName || 'Bác sĩ chuyên khoa'}
                    </span>
                    {getStatusBadge(appt.status)}
                  </div>
                  <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '0.6rem' }}>
                    Chuyên khoa: {appt.specialtyName || 'Đa khoa'}
                  </div>
                  <div style={{ display: 'flex', gap: '1.5rem', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                      <Calendar size={16} color="var(--primary)" />
                      {appt.slotDate}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                      <Clock size={16} color="var(--primary)" />
                      {appt.startTime.substring(0, 5)} - {appt.endTime.substring(0, 5)}
                    </div>
                  </div>
                </div>

                {/* Actions */}
                <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center' }}>
                  {(appt.status === 'CONFIRMED' || appt.status === 'IN_PROGRESS') && (
                    <button
                      id={`enter-room-btn-${appt.id}`}
                      className="btn btn-primary"
                      onClick={() => onEnterRoom(appt.id)}
                    >
                      <Video size={16} />
                      Vào phòng khám
                    </button>
                  )}

                  {canReschedule(appt) && (
                    <button
                      id={`reschedule-btn-${appt.id}`}
                      className="btn btn-secondary"
                      onClick={() => handleOpenReschedule(appt)}
                    >
                      <RefreshCw size={16} />
                      Đổi ca khám
                    </button>
                  )}

                  {appt.status === 'COMPLETED' && (
                    <button
                      id={`view-reports-btn-${appt.id}`}
                      className="btn btn-secondary"
                      onClick={() => handleViewReports(appt)}
                    >
                      <FileDown size={16} />
                      Tải báo cáo khám
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* RESCHEDULE MODAL */}
      {reschedulingAppt && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
          <div className="card" style={{ maxWidth: '600px', width: '100%', maxHeight: '90vh', overflowY: 'auto' }}>
            <div className="card-header">
              <h3 className="card-title">
                <RefreshCw size={18} color="var(--primary)" />
                Đổi ca khám cùng Bác sĩ
              </h3>
              <button className="btn btn-secondary" style={{ padding: '0.3rem 0.6rem' }} onClick={() => setReschedulingAppt(null)}>
                <X size={16} />
              </button>
            </div>

            <div className="alert alert-info" style={{ fontSize: '0.85rem' }}>
              Quy tắc đổi ca: Giữ nguyên bác sĩ và biểu mẫu y tế ban đầu. Miễn phí đổi ca. Phải thực hiện trước 00:00 ngày khám (giờ Việt Nam).
            </div>

            <div className="form-group">
              <label className="form-label">Chọn ngày khám mới (sau hôm nay):</label>
              <input
                type="date"
                value={rescheduleDate}
                min={new Date(Date.now() + 86400000).toISOString().split('T')[0]}
                onChange={e => setRescheduleDate(e.target.value)}
              />
            </div>

            <div style={{ marginBottom: '1.25rem' }}>
              <label className="form-label">Ca khám mới khả dụng:</label>
              {availableSlots.length === 0 ? (
                <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                  Bác sĩ chưa có ca trống vào ngày đã chọn. Vui lòng chọn ngày khác.
                </div>
              ) : (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: '0.6rem' }}>
                  {availableSlots.map(s => (
                    <div
                      key={s.id}
                      className={`slot-card ${selectedNewSlot?.id === s.id ? 'selected' : ''}`}
                      onClick={() => setSelectedNewSlot(s)}
                    >
                      <div style={{ fontWeight: 700, fontSize: '0.9rem' }}>
                        {s.startTime.substring(0, 5)} - {s.endTime.substring(0, 5)}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button className="btn btn-secondary" onClick={() => setReschedulingAppt(null)}>
                Hủy bỏ
              </button>
              <button
                id="confirm-reschedule-btn"
                className="btn btn-primary"
                disabled={!selectedNewSlot || rescheduleLoading}
                onClick={handleConfirmReschedule}
              >
                {rescheduleLoading ? 'Đang đổi ca...' : 'Xác nhận Đổi ca'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* REPORTS DOWNLOAD MODAL */}
      {reportModalAppt && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
          <div className="card" style={{ maxWidth: '540px', width: '100%' }}>
            <div className="card-header">
              <h3 className="card-title">
                <FileDown size={18} color="var(--primary)" />
                Báo cáo kết luận ca khám
              </h3>
              <button className="btn btn-secondary" style={{ padding: '0.3rem 0.6rem' }} onClick={() => setReportModalAppt(null)}>
                <X size={16} />
              </button>
            </div>

            {downloadLoading ? (
              <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                Đang tải danh sách báo cáo...
              </div>
            ) : encounterSummary?.files.length === 0 ? (
              <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                Chưa có tệp báo cáo nào được công bố cho ca khám này.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginBottom: '1.5rem' }}>
                {encounterSummary?.files.map(f => {
                  const name = f.originalName || f.fileName || 'Bao_cao_kham.pdf';
                  const size = ((f.sizeBytes ?? f.fileSizeBytes ?? 0) / 1024).toFixed(1);
                  const mime = f.mimeType || f.contentType || 'application/pdf';
                  return (
                    <div key={f.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.75rem 1rem', background: 'var(--bg-subtle)', borderRadius: 'var(--radius-md)' }}>
                      <div>
                        <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>{name}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                          {size} KB - {mime}
                        </div>
                      </div>
                      <button
                        className="btn btn-primary"
                        style={{ padding: '0.4rem 0.8rem', fontSize: '0.85rem' }}
                        onClick={() => handleDownloadFile(f.id, name)}
                      >
                        <FileDown size={14} />
                        Tải về
                      </button>
                    </div>
                  );
                })}
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button className="btn btn-secondary" onClick={() => setReportModalAppt(null)}>
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
