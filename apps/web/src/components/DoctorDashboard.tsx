import React, { useState, useEffect } from 'react';
import { toVietnamDateInput } from '../utils/date';
import { api } from '../services/api';
import { Appointment, IntakeForm, EncounterSummary } from '../types';
import { Calendar, Clock, User, FileText, Video, UploadCloud, CheckCircle, AlertCircle, X } from 'lucide-react';

interface DoctorDashboardProps {
  onEnterRoom: (appointmentId: string) => void;
}

export const DoctorDashboard: React.FC<DoctorDashboardProps> = ({ onEnterRoom }) => {
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [selectedDate, setSelectedDate] = useState<string>(() => toVietnamDateInput());
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // View Intake Modal
  const [viewingIntake, setViewingIntake] = useState<{ appt: Appointment; intake: IntakeForm } | null>(null);
  const [intakeLoading, setIntakeLoading] = useState<boolean>(false);

  // Complete & Report Modal
  const [completingAppt, setCompletingAppt] = useState<Appointment | null>(null);
  const [encounter, setEncounter] = useState<EncounterSummary | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState<boolean>(false);
  const [actionLoading, setActionLoading] = useState<boolean>(false);

  const fetchDoctorAppointments = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getDoctorAppointments(selectedDate);
      setAppointments(data);
    } catch (err: any) {
      setError(err.message || 'Không thể tải lịch khám của bác sĩ');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDoctorAppointments();
  }, [selectedDate]);

  // View Patient Intake Form
  const handleViewIntake = async (appt: Appointment) => {
    if (!appt.intakeId) {
      alert('Không tìm thấy biểu mẫu tiền khám');
      return;
    }
    setIntakeLoading(true);
    try {
      const data = await api.getIntake(appt.intakeId);
      setViewingIntake({ appt, intake: data });
    } catch (err: any) {
      alert(err.message || 'Không thể tải thông tin biểu mẫu y tế');
    } finally {
      setIntakeLoading(false);
    }
  };

  // Start Encounter (Enforces single IN_PROGRESS per doctor)
  const handleStartEncounter = async (appt: Appointment) => {
    setError(null);
    setSuccessMsg(null);
    try {
      await api.startEncounter(appt.id);
      setSuccessMsg('Đã bắt đầu ca khám! Đang mở phòng tư vấn...');
      onEnterRoom(appt.id);
    } catch (err: any) {
      setError(err.message || 'Bác sĩ đang có một ca khám khác chưa hoàn tất.');
    }
  };

  // Open Complete / Upload Report Modal
  const handleOpenCompleteModal = async (appt: Appointment) => {
    setEncounter(null);
    setSelectedFile(null);
    setError(null);
    setSuccessMsg(null);
    setCompletingAppt(appt);
    setActionLoading(true);
    try {
      const enc = await api.getEncounter(appt.id);
      setEncounter(enc);
    } catch (err: any) {
      // Encounter might not be created yet if direct from confirmed
      setEncounter(null);
    } finally {
      setActionLoading(false);
    }
  };

  // Upload Report File (DOCX/PDF/ZIP)
  const handleUploadReport = async () => {
    if (!completingAppt || !selectedFile) return;
    setUploading(true);
    setError(null);
    try {
      await api.uploadReportFile(completingAppt.id, selectedFile);
      setSelectedFile(null);
      const updated = await api.getEncounter(completingAppt.id);
      setEncounter(updated);
      setSuccessMsg('Tải lên tệp báo cáo thành công!');
    } catch (err: any) {
      setError(err.message || 'Tải lên tệp báo cáo thất bại (hỗ trợ DOCX, PDF, ZIP tối đa 20MB)');
    } finally {
      setUploading(false);
    }
  };

  // Publish Reports
  const handlePublish = async () => {
    if (!completingAppt) return;
    setActionLoading(true);
    setError(null);
    try {
      const updated = await api.publishReports(completingAppt.id);
      setEncounter(updated);
      setSuccessMsg('Đã công bố báo cáo kết luận cho bệnh nhân!');
    } catch (err: any) {
      setError(err.message || 'Công bố báo cáo thất bại');
    } finally {
      setActionLoading(false);
    }
  };

  // Complete Encounter
  const handleCompleteEncounter = async () => {
    if (!completingAppt || !encounter?.published || uploading) return;
    setActionLoading(true);
    setError(null);
    try {
      await api.completeEncounter(completingAppt.id);
      setSuccessMsg('Hoàn tất ca khám thành công!');
      setCompletingAppt(null);
      await fetchDoctorAppointments();
    } catch (err: any) {
      setError(err.message || 'Không thể hoàn tất ca: bắt buộc phải có ít nhất một báo cáo đã công bố.');
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 800 }}>Lịch trực & Ca khám của Bác sĩ</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
            Xem hồ sơ tiền khám, bắt đầu buổi tư vấn video và tải báo cáo kết luận
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <label style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Xem theo ngày:</label>
          <input
            id="doctor-filter-date"
            type="date"
            value={selectedDate}
            onChange={e => setSelectedDate(e.target.value)}
            style={{ width: 'auto' }}
          />
        </div>
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

      {loading ? (
        <div className="card" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
          Đang tải danh sách ca khám...
        </div>
      ) : appointments.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
          Không có ca khám nào được đặt vào ngày {selectedDate}.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {appointments.map(appt => (
            <div key={appt.id} className="card" id={`doctor-appt-${appt.id}`}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.35rem' }}>
                    <span style={{ fontWeight: 700, fontSize: '1.1rem' }}>
                      Ca khám #{appt.id.substring(0, 8)}
                    </span>
                    <span className={`badge badge-${appt.status.toLowerCase().replace('_', '-')}`}>
                      {appt.status}
                    </span>
                  </div>
                  <div style={{ display: 'flex', gap: '1.5rem', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                      <Calendar size={15} color="var(--primary)" /> {appt.slotDate}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                      <Clock size={15} color="var(--primary)" /> {appt.startTime.substring(0, 5)} - {appt.endTime.substring(0, 5)}
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center' }}>
                  {appt.intakeId && (
                    <button
                      id={`view-intake-btn-${appt.id}`}
                      className="btn btn-secondary"
                      onClick={() => handleViewIntake(appt)}
                    >
                      <FileText size={16} />
                      Xem biểu mẫu bệnh nhân
                    </button>
                  )}

                  {appt.status === 'CONFIRMED' && (
                    <button
                      id={`start-encounter-btn-${appt.id}`}
                      className="btn btn-primary"
                      onClick={() => handleStartEncounter(appt)}
                    >
                      <Video size={16} />
                      Bắt đầu khám
                    </button>
                  )}

                  {appt.status === 'IN_PROGRESS' && (
                    <>
                      <button
                        id={`resume-room-btn-${appt.id}`}
                        className="btn btn-primary"
                        onClick={() => onEnterRoom(appt.id)}
                      >
                        <Video size={16} />
                        Vào phòng khám
                      </button>
                      <button
                        id={`complete-modal-btn-${appt.id}`}
                        className="btn btn-secondary"
                        onClick={() => handleOpenCompleteModal(appt)}
                      >
                        <CheckCircle size={16} />
                        Báo cáo & Hoàn tất
                      </button>
                    </>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* VIEW INTAKE FORM MODAL */}
      {viewingIntake && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
          <div className="card" style={{ maxWidth: '640px', width: '100%', maxHeight: '90vh', overflowY: 'auto' }}>
            <div className="card-header">
              <h3 className="card-title">
                <FileText size={18} color="var(--primary)" />
                Biểu mẫu y tế trước khám của bệnh nhân
              </h3>
              <button className="btn btn-secondary" style={{ padding: '0.3rem 0.6rem' }} onClick={() => setViewingIntake(null)}>
                <X size={16} />
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginBottom: '1.5rem' }}>
              <div style={{ background: 'var(--bg-subtle)', padding: '0.9rem', borderRadius: 'var(--radius-md)' }}>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Lý do khám:</div>
                <div style={{ fontWeight: 600, fontSize: '1rem', marginTop: '0.2rem' }}>{viewingIntake.intake.reason}</div>
              </div>

              <div style={{ background: 'var(--bg-subtle)', padding: '0.9rem', borderRadius: 'var(--radius-md)' }}>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Triệu chứng chi tiết:</div>
                <div style={{ marginTop: '0.2rem' }}>{viewingIntake.intake.symptoms}</div>
              </div>

              <div className="grid-2">
                <div style={{ background: 'var(--bg-subtle)', padding: '0.9rem', borderRadius: 'var(--radius-md)' }}>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Tiền sử bệnh:</div>
                  <div style={{ marginTop: '0.2rem' }}>{viewingIntake.intake.medicalHistory}</div>
                </div>
                <div style={{ background: 'var(--bg-subtle)', padding: '0.9rem', borderRadius: 'var(--radius-md)' }}>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Bệnh nền:</div>
                  <div style={{ marginTop: '0.2rem' }}>{viewingIntake.intake.underlyingConditions || viewingIntake.intake.underlyingDiseases || 'Không có'}</div>
                </div>
              </div>

              {(viewingIntake.intake.symptomStartedAt || viewingIntake.intake.symptomOnset || viewingIntake.intake.currentMedications || viewingIntake.intake.drugAllergies) && (
                <div style={{ background: 'var(--bg-subtle)', padding: '0.9rem', borderRadius: 'var(--radius-md)' }}>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '0.4rem' }}>Thông tin bổ sung:</div>
                  {(viewingIntake.intake.symptomStartedAt || viewingIntake.intake.symptomOnset) && (
                    <div><strong>Khởi phát:</strong> {viewingIntake.intake.symptomStartedAt || viewingIntake.intake.symptomOnset}</div>
                  )}
                  {viewingIntake.intake.currentMedications && <div><strong>Thuốc đang dùng:</strong> {viewingIntake.intake.currentMedications}</div>}
                  {viewingIntake.intake.drugAllergies && <div><strong>Dị ứng:</strong> {viewingIntake.intake.drugAllergies}</div>}
                </div>
              )}
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button className="btn btn-secondary" onClick={() => setViewingIntake(null)}>Đóng</button>
            </div>
          </div>
        </div>
      )}

      {/* COMPLETE ENCOUNTER & REPORT UPLOAD MODAL */}
      {completingAppt && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' }}>
          <div className="card" style={{ maxWidth: '640px', width: '100%', maxHeight: '90vh', overflowY: 'auto' }}>
            <div className="card-header">
              <h3 className="card-title">
                <UploadCloud size={18} color="var(--primary)" />
                Báo cáo kết luận & Hoàn tất ca khám
              </h3>
              <button className="btn btn-secondary" style={{ padding: '0.3rem 0.6rem' }} onClick={() => setCompletingAppt(null)}>
                <X size={16} />
              </button>
            </div>

            <div className="alert alert-info" style={{ fontSize: '0.85rem' }}>
              Quy định hoàn tất: Bác sĩ tải lên file kết luận (DOCX/PDF/ZIP tối đa 20MB), nhấn "Công bố báo cáo", sau đó nhấn "Hoàn tất ca khám".
            </div>

            {error && (
              <div className="alert alert-danger" role="alert">
                <AlertCircle size={20} />
                <div>{error}</div>
              </div>
            )}

            {/* Upload form */}
            <div style={{ background: 'var(--bg-subtle)', padding: '1rem', borderRadius: 'var(--radius-md)', marginBottom: '1.25rem' }}>
              <div style={{ fontWeight: 600, fontSize: '0.9rem', marginBottom: '0.5rem' }}>Tải lên tệp báo cáo mới:</div>
              <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
                <input
                  id="report-file-input"
                  type="file"
                  accept=".docx,.pdf,.zip"
                  onChange={e => setSelectedFile(e.target.files ? e.target.files[0] : null)}
                  style={{ flex: 1 }}
                />
                <button
                  id="upload-report-btn"
                  className="btn btn-secondary"
                  disabled={!selectedFile || uploading}
                  onClick={handleUploadReport}
                >
                  {uploading ? 'Đang tải...' : 'Tải lên'}
                </button>
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.3rem' }}>
                Định dạng cho phép: DOCX, PDF, ZIP. Dung lượng tối đa: 20 MB.
              </div>
            </div>

            {/* Existing uploaded files list */}
            <div style={{ marginBottom: '1.5rem' }}>
              <div style={{ fontWeight: 600, fontSize: '0.9rem', marginBottom: '0.5rem' }}>
                Danh sách tệp báo cáo ({encounter?.files.length || 0}):
              </div>
              {encounter?.files.length === 0 ? (
                <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                  Chưa có tệp báo cáo nào được tải lên.
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                  {encounter?.files.map(f => (
                    <div key={f.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.6rem 0.9rem', background: 'rgba(15,23,42,0.6)', borderRadius: 'var(--radius-sm)' }}>
                      <span style={{ fontSize: '0.9rem' }}>
                        {f.originalName || f.fileName} ({(((f.sizeBytes ?? f.fileSizeBytes ?? 0)) / 1024).toFixed(1)} KB)
                      </span>
                      <span style={{ color: 'var(--success)', fontSize: '0.75rem' }}>Đã lưu</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Publish state */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', padding: '0.75rem 1rem', background: 'var(--bg-subtle)', borderRadius: 'var(--radius-md)' }}>
              <div>
                <div style={{ fontSize: '0.9rem', fontWeight: 600 }}>Trạng thái công bố:</div>
                <div style={{ fontSize: '0.8rem', color: encounter?.published ? 'var(--success)' : 'var(--warning)' }}>
                  {encounter?.published ? 'Đã công bố cho bệnh nhân' : 'Chưa công bố'}
                </div>
              </div>
              <button
                id="publish-reports-btn"
                className="btn btn-secondary"
                disabled={!encounter || encounter.files.length === 0 || encounter.published || actionLoading || uploading}
                onClick={handlePublish}
              >
                Công bố báo cáo
              </button>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button className="btn btn-secondary" onClick={() => setCompletingAppt(null)}>
                Đóng
              </button>
              <button
                id="complete-encounter-confirm-btn"
                className="btn btn-primary"
                disabled={!encounter?.published || actionLoading || uploading}
                onClick={handleCompleteEncounter}
              >
                {actionLoading ? 'Đang hoàn tất...' : 'Hoàn tất Ca khám'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
