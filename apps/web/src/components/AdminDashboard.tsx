import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { Specialty, Doctor, EmailLog, Slot, Appointment } from '../types';
import { ShieldCheck, UserPlus, FolderPlus, Clock, Mail, CheckCircle, AlertCircle, ExternalLink, Trash2, RefreshCw } from 'lucide-react';
import { addVietnamDays } from '../utils/date';

export const AdminDashboard: React.FC = () => {
  const [tab, setTab] = useState<'DOCTORS' | 'SPECIALTIES' | 'SLOTS' | 'DEMO_APPOINTMENTS' | 'EMAILS'>('DOCTORS');

  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [emailLogs, setEmailLogs] = useState<EmailLog[]>([]);
  const [adminSlots, setAdminSlots] = useState<Slot[]>([]);
  const [demoAppointments, setDemoAppointments] = useState<Appointment[]>([]);
  const [demoLoading, setDemoLoading] = useState(false);

  const loadDemoAppointments = async () => {
    setDemoLoading(true);
    try {
      setDemoAppointments(await api.getAdminDemoAppointments());
    } catch (err: any) {
      setError(err.message || 'Không thể tải lịch khám demo');
    } finally {
      setDemoLoading(false);
    }
  };

  // Create Doctor Form State
  const [doctorForm, setDoctorForm] = useState({
    fullName: '',
    email: '',
    password: 'doctor123',
    specialtyId: '',
    licenseNumber: '',
    experienceYears: 5,
    bio: ''
  });

  // Create Specialty Form State
  const [specialtyForm, setSpecialtyForm] = useState({
    code: '',
    name: '',
    description: ''
  });

  // Create Slot Form State
  const [slotForm, setSlotForm] = useState({
    doctorId: '',
    slotDate: addVietnamDays(1),
    startTime: '09:00:00',
    endTime: '09:30:00'
  });

  const [loading, setLoading] = useState<boolean>(false);
  const [slotsLoading, setSlotsLoading] = useState<boolean>(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [slotRefreshKey, setSlotRefreshKey] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const loadData = async () => {
    try {
      const [specs, docs] = await Promise.all([
        api.getAdminSpecialties(),
        api.getDoctors()
      ]);
      setSpecialties(specs);
      setDoctors(docs);
      setDoctorForm(prev => ({
        ...prev,
        specialtyId: specs.some(spec => spec.id === prev.specialtyId)
          ? prev.specialtyId
          : specs[0]?.id || ''
      }));
      setSlotForm(prev => ({
        ...prev,
        doctorId: docs.some(doctor => doctor.id === prev.doctorId)
          ? prev.doctorId
          : docs[0]?.id || ''
      }));
    } catch (err: any) {
      setError(err.message || 'Lỗi tải dữ liệu');
    }
  };

  const loadEmailLogs = async () => {
    try {
      const logs = await api.getEmailLogs();
      setEmailLogs(logs);
    } catch (err: any) {
      console.warn('Could not load email logs:', err);
    }
  };

  useEffect(() => {
    loadData();
    loadEmailLogs();
  }, []);

  useEffect(() => {
    let active = true;

    if (tab !== 'SLOTS' || !slotForm.doctorId || !slotForm.slotDate) {
      setAdminSlots([]);
      setSlotsLoading(false);
      return () => {
        active = false;
      };
    }

    setSlotsLoading(true);
    api.getAvailableSlots(slotForm.doctorId, slotForm.slotDate)
      .then(slots => {
        if (active) setAdminSlots(slots);
      })
      .catch((err: any) => {
        if (active) setError(err.message || 'Không thể tải danh sách ca khám');
      })
      .finally(() => {
        if (active) setSlotsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [tab, slotForm.doctorId, slotForm.slotDate, slotRefreshKey]);

  // Handle Create Doctor
  const handleCreateDoctor = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setSuccessMsg(null);
    try {
      await api.createDoctor(doctorForm);
      setSuccessMsg(`Tạo tài khoản Bác sĩ ${doctorForm.fullName} thành công!`);
      setDoctorForm({
        fullName: '',
        email: '',
        password: 'doctor123',
        specialtyId: specialties[0]?.id || '',
        licenseNumber: '',
        experienceYears: 5,
        bio: ''
      });
      await loadData();
    } catch (err: any) {
      setError(err.message || 'Tạo bác sĩ thất bại');
    } finally {
      setLoading(false);
    }
  };

  // Handle Create Specialty
  const handleCreateSpecialty = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setSuccessMsg(null);
    try {
      await api.createSpecialty(specialtyForm);
      setSuccessMsg(`Tạo chuyên khoa ${specialtyForm.name} thành công!`);
      setSpecialtyForm({ code: '', name: '', description: '' });
      await loadData();
    } catch (err: any) {
      setError(err.message || 'Tạo chuyên khoa thất bại');
    } finally {
      setLoading(false);
    }
  };

  // Handle Create Slot
  const handleCreateSlot = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setSuccessMsg(null);
    try {
      await api.createSlot(slotForm);
      setSuccessMsg('Tạo ca khám thành công!');
      setSlotRefreshKey(value => value + 1);
    } catch (err: any) {
      setError(err.message || 'Tạo ca khám thất bại');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteDoctor = async (doctor: Doctor) => {
    const confirmed = window.confirm(
      `Xóa bác sĩ ${doctor.fullName}? Bác sĩ sẽ không còn xuất hiện khi đặt khám và các ca chưa sử dụng sẽ bị khóa.`
    );
    if (!confirmed) return;

    setDeletingId(doctor.id);
    setError(null);
    setSuccessMsg(null);
    try {
      await api.deleteDoctor(doctor.id, doctor.userId);
      setSuccessMsg(`Đã xóa bác sĩ ${doctor.fullName}.`);
      await loadData();
      setSlotRefreshKey(value => value + 1);
    } catch (err: any) {
      setError(err.message || 'Xóa bác sĩ thất bại');
    } finally {
      setDeletingId(null);
    }
  };

  const handleDeleteSlot = async (slot: Slot) => {
    const start = slot.startTime?.substring(0, 5) || '';
    const confirmed = window.confirm(`Xóa ca khám ${start} ngày ${slot.slotDate}?`);
    if (!confirmed) return;

    setDeletingId(slot.id);
    setError(null);
    setSuccessMsg(null);
    try {
      await api.deleteSlot(slot.id);
      setSuccessMsg(`Đã xóa ca khám ${start} ngày ${slot.slotDate}.`);
      setSlotRefreshKey(value => value + 1);
    } catch (err: any) {
      setError(err.message || 'Xóa ca khám thất bại');
    } finally {
      setDeletingId(null);
    }
  };

  const handleDeleteDemoAppointment = async (appointment: Appointment) => {
    if (!appointment.demo || !window.confirm(
      `Xóa lịch khám DEMO #${appointment.id.substring(0, 8)}? Ca sẽ được gỡ khỏi danh sách và giải phóng chỗ. Hồ sơ, báo cáo và dữ liệu lịch sử vẫn được giữ.`
    )) return;
    setDeletingId(appointment.id);
    setError(null);
    setSuccessMsg(null);
    try {
      await api.deleteDemoAppointment(appointment.id);
      setSuccessMsg(`Đã xóa lịch khám demo #${appointment.id.substring(0, 8)}.`);
      await loadDemoAppointments();
      setSlotRefreshKey(value => value + 1);
    } catch (err: any) {
      setError(err.message || 'Không thể xóa lịch khám demo');
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 800, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <ShieldCheck size={24} color="var(--primary)" />
            Bảng Điều Khiển Quản Trị Hệ Thống
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
            Quản lý tài khoản bác sĩ, chuyên khoa, lịch ca trực và theo dõi email xác nhận
          </p>
        </div>

        {/* Sub tabs */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem', background: 'var(--bg-subtle)', padding: '0.25rem', borderRadius: 'var(--radius-md)' }}>
          <button
            className={`btn ${tab === 'DOCTORS' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem' }}
            onClick={() => setTab('DOCTORS')}
          >
            Bác sĩ ({doctors.length})
          </button>
          <button
            className={`btn ${tab === 'SPECIALTIES' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem' }}
            onClick={() => setTab('SPECIALTIES')}
          >
            Chuyên khoa ({specialties.length})
          </button>
          <button
            className={`btn ${tab === 'SLOTS' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem' }}
            onClick={() => setTab('SLOTS')}
          >
            Tạo ca khám
          </button>
          <button
            className={`btn ${tab === 'EMAILS' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem' }}
            onClick={() => { setTab('EMAILS'); loadEmailLogs(); }}
          >
            Email logs ({emailLogs.length})
          </button>
          <button
            id="admin-demo-appointments-tab"
            className={`btn ${tab === 'DEMO_APPOINTMENTS' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem' }}
            onClick={() => { setTab('DEMO_APPOINTMENTS'); void loadDemoAppointments(); }}
          >
            Lịch khám demo
          </button>
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

      {/* TAB 1: DOCTORS */}
      {tab === 'DOCTORS' && (
        <div className="grid-2">
          {/* Create Doctor Form */}
          <div className="card">
            <div className="card-header">
              <h3 className="card-title">
                <UserPlus size={18} color="var(--primary)" />
                Thêm Bác Sĩ Mới
              </h3>
            </div>
            <form onSubmit={handleCreateDoctor}>
              <div className="form-group">
                <label className="form-label">Họ và tên bác sĩ</label>
                <input
                  id="admin-doc-name"
                  type="text"
                  required
                  placeholder="BS. Trần Văn Bình"
                  value={doctorForm.fullName}
                  onChange={e => setDoctorForm({ ...doctorForm, fullName: e.target.value })}
                />
              </div>

              <div className="grid-2">
                <div className="form-group">
                  <label className="form-label">Email đăng nhập</label>
                  <input
                    id="admin-doc-email"
                    type="email"
                    required
                    placeholder="doctor.binh@telehealth.vn"
                    value={doctorForm.email}
                    onChange={e => setDoctorForm({ ...doctorForm, email: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Mật khẩu mặc định</label>
                  <input
                    id="admin-doc-pass"
                    type="password"
                    required
                    value={doctorForm.password}
                    onChange={e => setDoctorForm({ ...doctorForm, password: e.target.value })}
                  />
                </div>
              </div>

              <div className="grid-2">
                <div className="form-group">
                  <label className="form-label">Chuyên khoa</label>
                  <select
                    id="admin-doc-spec"
                    value={doctorForm.specialtyId}
                    onChange={e => setDoctorForm({ ...doctorForm, specialtyId: e.target.value })}
                  >
                    {specialties.map(s => (
                      <option key={s.id} value={s.id}>{s.name}</option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Số chứng chỉ hành nghề</label>
                  <input
                    id="admin-doc-license"
                    type="text"
                    required
                    placeholder="CCHN-88992"
                    value={doctorForm.licenseNumber}
                    onChange={e => setDoctorForm({ ...doctorForm, licenseNumber: e.target.value })}
                  />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Số năm kinh nghiệm</label>
                <input
                  id="admin-doc-exp"
                  type="number"
                  min="1"
                  required
                  value={doctorForm.experienceYears}
                  onChange={e => setDoctorForm({ ...doctorForm, experienceYears: Number(e.target.value) })}
                />
              </div>

              <button id="admin-create-doc-btn" type="submit" className="btn btn-primary" disabled={loading} style={{ width: '100%' }}>
                {loading ? 'Đang tạo...' : 'Tạo Tài Khoản Bác Sĩ'}
              </button>
            </form>
          </div>

          {/* Doctors List */}
          <div className="card">
            <div className="card-header">
              <h3 className="card-title">Danh sách Bác sĩ ({doctors.length})</h3>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', maxHeight: '500px', overflowY: 'auto' }}>
              {doctors.length === 0 && (
                <div style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--text-muted)' }}>
                  Chưa có bác sĩ đang hoạt động.
                </div>
              )}
              {doctors.map(d => (
                <div key={d.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '0.75rem', padding: '0.9rem', background: 'var(--bg-subtle)', borderRadius: 'var(--radius-md)' }}>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 700, fontSize: '1rem' }}>{d.fullName}</div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--primary)', marginTop: '0.15rem' }}>
                      {d.specialtyName || 'Chuyên khoa'}
                      {d.experienceYears != null ? ` • ${d.experienceYears} năm kinh nghiệm` : ''}
                    </div>
                    {d.licenseNumber && (
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.3rem' }}>
                        Số CCHN: {d.licenseNumber}
                      </div>
                    )}
                  </div>
                  <button
                    id={`admin-delete-doctor-${d.id}`}
                    type="button"
                    className="btn btn-danger"
                    style={{ flexShrink: 0, padding: '0.45rem 0.7rem', fontSize: '0.8rem' }}
                    disabled={deletingId === d.id}
                    onClick={() => handleDeleteDoctor(d)}
                  >
                    <Trash2 size={15} />
                    {deletingId === d.id ? 'Đang xóa...' : 'Xóa'}
                  </button>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: SPECIALTIES */}
      {tab === 'SPECIALTIES' && (
        <div className="grid-2">
          <div className="card">
            <div className="card-header">
              <h3 className="card-title">
                <FolderPlus size={18} color="var(--primary)" />
                Thêm Chuyên Khoa Mới
              </h3>
            </div>
            <form onSubmit={handleCreateSpecialty}>
              <div className="form-group">
                <label className="form-label">Mã chuyên khoa (code)</label>
                <input
                  id="spec-code-input"
                  type="text"
                  required
                  placeholder="TIM_MACH"
                  value={specialtyForm.code}
                  onChange={e => setSpecialtyForm({ ...specialtyForm, code: e.target.value.toUpperCase() })}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Tên chuyên khoa</label>
                <input
                  id="spec-name-input"
                  type="text"
                  required
                  placeholder="Tim mạch"
                  value={specialtyForm.name}
                  onChange={e => setSpecialtyForm({ ...specialtyForm, name: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Mô tả ngắn</label>
                <textarea
                  id="spec-desc-input"
                  rows={2}
                  placeholder="Khám và điều trị các bệnh lý tim mạch, huyết áp..."
                  value={specialtyForm.description}
                  onChange={e => setSpecialtyForm({ ...specialtyForm, description: e.target.value })}
                />
              </div>
              <button id="admin-create-spec-btn" type="submit" className="btn btn-primary" disabled={loading} style={{ width: '100%' }}>
                {loading ? 'Đang tạo...' : 'Tạo Chuyên Khoa'}
              </button>
            </form>
          </div>

          <div className="card">
            <div className="card-header">
              <h3 className="card-title">Danh sách Chuyên khoa ({specialties.length})</h3>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {specialties.map(s => (
                <div key={s.id} style={{ padding: '0.9rem', background: 'var(--bg-subtle)', borderRadius: 'var(--radius-md)' }}>
                  <div style={{ fontWeight: 700, fontSize: '0.95rem' }}>
                    {s.name}{s.code ? ` (${s.code})` : ''}
                  </div>
                  {s.description && (
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.2rem' }}>{s.description}</div>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* TAB 3: SLOTS */}
      {tab === 'SLOTS' && (
        <div className="grid-2">
          <div className="card">
            <div className="card-header">
              <h3 className="card-title">
                <Clock size={18} color="var(--primary)" />
                Mở Ca Khám Mới Cho Bác Sĩ
              </h3>
            </div>
            <form onSubmit={handleCreateSlot}>
              <div className="form-group">
                <label className="form-label">Bác sĩ phụ trách</label>
                <select
                  id="slot-doc-select"
                  value={slotForm.doctorId}
                  disabled={doctors.length === 0}
                  onChange={e => setSlotForm({ ...slotForm, doctorId: e.target.value })}
                >
                  {doctors.length === 0 && <option value="">Chưa có bác sĩ</option>}
                  {doctors.map(d => (
                    <option key={d.id} value={d.id}>{d.fullName} ({d.specialtyName})</option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Ngày khám</label>
                <input
                  id="slot-date-input"
                  type="date"
                  required
                  value={slotForm.slotDate}
                  onChange={e => setSlotForm({ ...slotForm, slotDate: e.target.value })}
                />
              </div>

              <div className="grid-2">
                <div className="form-group">
                  <label className="form-label">Giờ bắt đầu</label>
                  <input
                    id="slot-start-time"
                    type="time"
                    step="60"
                    required
                    value={slotForm.startTime.substring(0, 5)}
                    onChange={e => setSlotForm({ ...slotForm, startTime: `${e.target.value}:00` })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Giờ kết thúc</label>
                  <input
                    id="slot-end-time"
                    type="time"
                    step="60"
                    required
                    value={slotForm.endTime.substring(0, 5)}
                    onChange={e => setSlotForm({ ...slotForm, endTime: `${e.target.value}:00` })}
                  />
                </div>
              </div>

              <button id="admin-create-slot-btn" type="submit" className="btn btn-primary" disabled={loading || !slotForm.doctorId} style={{ width: '100%' }}>
                {loading ? 'Đang tạo...' : 'Tạo Ca Khám (Slot)'}
              </button>
            </form>
          </div>

          <div className="card">
            <div className="card-header">
              <h3 className="card-title">
                Danh sách ca ngày {slotForm.slotDate} ({adminSlots.length})
              </h3>
              <button
                type="button"
                className="btn btn-secondary"
                style={{ padding: '0.4rem 0.65rem' }}
                disabled={slotsLoading || !slotForm.doctorId}
                onClick={() => setSlotRefreshKey(value => value + 1)}
                title="Tải lại danh sách ca"
              >
                <RefreshCw size={15} />
                Làm mới
              </button>
            </div>

            {slotsLoading ? (
              <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                Đang tải danh sách ca khám...
              </div>
            ) : adminSlots.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                Bác sĩ chưa có ca khám trong ngày này.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', maxHeight: '430px', overflowY: 'auto' }}>
                {adminSlots.map(slot => (
                  <div key={slot.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '0.75rem', padding: '0.9rem', background: 'var(--bg-subtle)', borderRadius: 'var(--radius-md)' }}>
                    <div>
                      <div style={{ fontWeight: 700 }}>
                        {slot.startTime.substring(0, 5)} - {slot.endTime.substring(0, 5)}
                      </div>
                      <div style={{ color: slot.available === false ? 'var(--warning)' : 'var(--success)', fontSize: '0.8rem', marginTop: '0.2rem' }}>
                        {slot.available === false ? 'Đã được giữ hoặc đặt' : 'Còn trống'}
                      </div>
                    </div>
                    <button
                      id={`admin-delete-slot-${slot.id}`}
                      type="button"
                      className="btn btn-danger"
                      style={{ padding: '0.45rem 0.7rem', fontSize: '0.8rem' }}
                      disabled={deletingId === slot.id}
                      onClick={() => handleDeleteSlot(slot)}
                    >
                      <Trash2 size={15} />
                      {deletingId === slot.id ? 'Đang xóa...' : 'Xóa ca'}
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {tab === 'DEMO_APPOINTMENTS' && (
        <div className="card">
          <div className="card-header">
            <h3 className="card-title">Lịch khám demo ({demoAppointments.length})</h3>
            <button className="btn btn-secondary" disabled={demoLoading} onClick={() => void loadDemoAppointments()}>
              <RefreshCw size={16} /> Làm mới
            </button>
          </div>
          <div className="alert alert-info">
            Chỉ các ca của hai tài khoản Bệnh nhân 1 và Bệnh nhân 2 dùng thử mới được xóa ở đây.
            Lịch khám của tài khoản thực tế không có quyền xóa bằng chức năng này.
          </div>
          {demoLoading ? <p>Đang tải...</p> : demoAppointments.length === 0 ? <p>Không còn lịch khám demo.</p> : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {demoAppointments.map(appointment => (
                <div key={appointment.id} style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: '0.75rem', padding: '1rem', background: 'var(--bg-subtle)', borderRadius: 'var(--radius-md)' }}>
                  <div>
                    <strong>#{appointment.id.substring(0, 8)} · {appointment.doctorName}</strong>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '0.35rem' }}>
                      {appointment.startsAt && new Date(appointment.startsAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false })}
                      {' · '}{appointment.status}{' · '}
                      {appointment.patientId === '33333333-3333-3333-3333-333333333333' ? 'Bệnh nhân demo 1' : 'Bệnh nhân demo 2'}
                    </div>
                  </div>
                  <button id={`admin-delete-demo-${appointment.id}`} className="btn btn-danger"
                    disabled={!appointment.demo || deletingId !== null}
                    onClick={() => void handleDeleteDemoAppointment(appointment)}>
                    <Trash2 size={16} /> {deletingId === appointment.id ? 'Đang xóa...' : 'Xóa lịch demo'}
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 4: EMAILS */}
      {tab === 'EMAILS' && (
        <div className="card">
          <div className="card-header">
            <h3 className="card-title">
              <Mail size={18} color="var(--primary)" />
              Nhật ký Email Xác Nhận (Spring Mail / Mailpit)
            </h3>
            <a
              href="http://localhost:8025"
              target="_blank"
              rel="noreferrer"
              className="btn btn-secondary"
              style={{ fontSize: '0.85rem', padding: '0.4rem 0.8rem' }}
            >
              Mở Mailpit Web UI <ExternalLink size={14} />
            </a>
          </div>

          <div className="alert alert-info" style={{ fontSize: '0.85rem' }}>
            Thông báo bảo mật: Email chỉ gửi thông tin thời gian khám và mã tra cứu; tuyệt đối không kèm bệnh án, triệu chứng hay đơn thuốc.
          </div>

          {emailLogs.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
              Chưa có email nào trong nhật ký. Thực hiện đặt lịch hoặc đổi ca khám để gửi email xác nhận.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
              {emailLogs.map(log => (
                <div key={log.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.85rem 1rem', background: 'var(--bg-subtle)', borderRadius: 'var(--radius-md)' }}>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: '0.95rem' }}>{log.subject}</div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.2rem' }}>
                      Đến: {log.recipientEmail} • Lúc: {new Date(log.createdAt).toLocaleString('vi-VN')}
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <span className={`badge ${log.status === 'SENT' ? 'badge-confirmed' : 'badge-pending'}`}>
                      {log.status}
                    </span>
                    {log.retryCount > 0 && <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Retry: {log.retryCount}</span>}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
