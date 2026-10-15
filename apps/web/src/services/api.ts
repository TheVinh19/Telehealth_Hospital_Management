import {
  User,
  Specialty,
  Doctor,
  Slot,
  Appointment,
  IntakeForm,
  PaymentOrder,
  ChatMessage,
  EncounterSummary,
  EmailLog
} from '../types';

const BASE_URL = '/api/v1';

class ApiService {
  private token: string | null = null;

  constructor() {
    this.token = localStorage.getItem('auth_token');
  }

  setToken(token: string | null) {
    this.token = token;
    if (token) {
      localStorage.setItem('auth_token', token);
    } else {
      localStorage.removeItem('auth_token');
    }
  }

  getToken(): string | null {
    return this.token;
  }

  private async request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const headers = new Headers(options.headers || {});
    if (this.token && !headers.has('Authorization')) {
      headers.set('Authorization', `Bearer ${this.token}`);
    }
    if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json');
    }

    const response = await fetch(`${BASE_URL}${endpoint}`, {
      ...options,
      headers
    });

    if (!response.ok) {
      let errorMessage = `Yêu cầu thất bại (${response.status})`;
      try {
        const errorData = await response.json();
        errorMessage = errorData.message || errorData.error || errorMessage;
      } catch {
        // ignore fallback
      }
      throw new Error(errorMessage);
    }

    if (response.status === 204) {
      return {} as T;
    }

    const json = await response.json();
    if (json && typeof json === 'object') {
      if ('success' in json && (json as any).success === false) {
        throw new Error((json as any).message || 'Thao tác không thành công');
      }
      if ('data' in json && (json as any).data !== undefined) {
        const d = (json as any).data;
        if (d && typeof d === 'object') {
          if ('user' in d && d.user) {
            d.user.fullName = d.user.displayName || d.user.fullName || d.user.email;
          }
          if ('displayName' in d && !('fullName' in d)) {
            d.fullName = d.displayName;
          }
        }
        return d as T;
      }
    }
    return json as T;
  }

  // --- Auth ---
  async login(email: string, password: string): Promise<{ accessToken: string; refreshToken: string; user: User }> {
    const res = await this.request<{ accessToken: string; refreshToken: string; user: User }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    });
    this.setToken(res.accessToken);
    return res;
  }

  async register(data: { email: string; password: string; fullName: string; phone?: string; role?: string }): Promise<{ accessToken: string; user: User }> {
    // Registration creates a patient but returns only UserResponse, not a session.
    await this.request<User>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        email: data.email,
        password: data.password,
        displayName: data.fullName
      })
    });
    try {
      return await this.login(data.email, data.password);
    } catch {
      throw new Error('Tài khoản đã được tạo nhưng chưa đăng nhập được. Vui lòng chọn Đăng nhập và dùng email, mật khẩu vừa đăng ký.');
    }
  }

  async getMe(): Promise<User> {
    return this.request<User>('/auth/me');
  }

  // --- Specialties & Doctors ---
  async getSpecialties(): Promise<Specialty[]> {
    return this.request<Specialty[]>('/specialties');
  }

  async getAdminSpecialties(): Promise<Specialty[]> {
    return this.request<Specialty[]>('/admin/specialties');
  }

  async createSpecialty(data: { code: string; name: string; description?: string }): Promise<Specialty> {
    return this.request<Specialty[]>('/admin/specialties', {
      method: 'POST',
      body: JSON.stringify(data)
    }).then(res => (Array.isArray(res) ? res[0] : (res as any)));
  }

  async getDoctors(specialtyId?: string): Promise<Doctor[]> {
    const query = specialtyId ? `?specialtyId=${specialtyId}` : '';
    const doctors = await this.request<Doctor[]>(`/doctors${query}`);
    return (doctors || []).map(doctor => ({
      ...doctor,
      fullName: doctor.fullName || doctor.displayName || 'Bác sĩ'
    }));
  }

  async createDoctor(data: {
    email: string;
    fullName: string;
    password: string;
    specialtyId: string;
    licenseNumber: string;
    experienceYears: number;
    bio?: string;
  }): Promise<Doctor> {
    const user = await this.request<User>('/admin/users/doctor', {
      method: 'POST',
      body: JSON.stringify({
        email: data.email,
        password: data.password,
        displayName: data.fullName
      })
    });

    const profile = await this.request<Doctor>('/admin/doctors', {
      method: 'POST',
      body: JSON.stringify({
        userId: user.id,
        specialtyId: data.specialtyId,
        displayName: data.fullName,
        bio: data.bio
      })
    });

    return {
      ...profile,
      fullName: profile.fullName || profile.displayName || data.fullName,
      experienceYears: data.experienceYears,
      licenseNumber: data.licenseNumber
    };
  }

  private mapAppointment(appt: any): Appointment {
    if (!appt) return appt;
    if (appt.startsAt) {
      const d = new Date(appt.startsAt);
      appt.slotDate = appt.slotDate || d.toISOString().split('T')[0];
      appt.startTime = appt.startTime || d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      const endD = new Date(d.getTime() + 30 * 60000);
      appt.endTime = appt.endTime || endD.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }
    appt.paymentExpiresAt = appt.expiresAt || appt.paymentExpiresAt;
    appt.totalFee = appt.amountVnd || appt.totalFee || 100000;
    return appt;
  }

  // --- Slots ---
  async getAvailableSlots(doctorId: string, date: string): Promise<Slot[]> {
    const res = await this.request<Slot[]>(`/doctors/${doctorId}/slots?date=${date}`);
    return (res || []).map(s => {
      if (s.startsAt) {
        const d = new Date(s.startsAt);
        s.slotDate = s.slotDate || d.toISOString().split('T')[0];
        s.startTime = s.startTime || d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        const endD = new Date(d.getTime() + 30 * 60000);
        s.endTime = s.endTime || endD.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      }
      return s;
    });
  }

  async createSlot(data: {
    doctorId: string;
    slotDate: string;
    startTime: string;
    endTime: string;
  }): Promise<Slot> {
    const startsAt = new Date(`${data.slotDate}T${data.startTime}`).toISOString();
    return this.request<Slot>('/admin/slots', {
      method: 'POST',
      body: JSON.stringify({
        doctorId: data.doctorId,
        startsAt
      })
    });
  }

  async deleteDoctor(doctorId: string, userId: string): Promise<void> {
    await this.request<void>(`/admin/doctors/${doctorId}`, {
      method: 'DELETE'
    });
    await this.request<void>(`/admin/users/${userId}`, {
      method: 'DELETE'
    });
  }

  async deleteSlot(slotId: string): Promise<void> {
    await this.request<void>(`/admin/slots/${slotId}`, {
      method: 'DELETE'
    });
  }

  // --- Clinical Intakes ---
  async submitIntake(data: IntakeForm): Promise<{ intakeId: string }> {
    const response = await this.request<{ id?: string; intakeId?: string }>('/intakes', {
      method: 'POST',
      body: JSON.stringify({
        ...data,
        underlyingConditions: data.underlyingConditions || data.underlyingDiseases,
        symptomStartedAt: data.symptomStartedAt || data.symptomOnset
      })
    });
    const intakeId = response.intakeId || response.id;
    if (!intakeId) {
      throw new Error('Phản hồi tạo biểu mẫu không có mã intake');
    }
    return { intakeId };
  }

  async getIntake(intakeId: string): Promise<IntakeForm & { id: string }> {
    const response = await this.request<any>(`/intakes/${intakeId}`);
    return {
      ...(response.payload || response),
      id: response.id || intakeId
    };
  }

  // --- Appointments ---
  async getAdminDemoAppointments(): Promise<Appointment[]> {
    const appointments = await this.request<Appointment[]>('/admin/appointments/demo');
    return (appointments || []).map(appointment => this.mapAppointment(appointment));
  }

  async deleteDemoAppointment(appointmentId: string): Promise<void> {
    await this.request<void>(`/admin/appointments/${appointmentId}`, { method: 'DELETE' });
  }

  async holdAppointment(doctorId: string, slotId: string, intakeId: string): Promise<Appointment> {
    const res = await this.request<any>('/appointments', {
      method: 'POST',
      body: JSON.stringify({ doctorId, slotId, intakeId })
    });
    return this.mapAppointment(res);
  }

  async getMyAppointments(): Promise<Appointment[]> {
    const res = await this.request<any[]>('/appointments');
    return (res || []).map(a => this.mapAppointment(a));
  }

  async getDoctorAppointments(date?: string): Promise<Appointment[]> {
    const res = await this.request<any[]>('/appointments');
    let list = (res || []).map(a => this.mapAppointment(a));
    if (date) {
      list = list.filter(a => a.slotDate === date);
    }
    return list;
  }

  async getAppointment(id: string): Promise<Appointment> {
    const res = await this.request<any>(`/appointments/${id}`);
    return this.mapAppointment(res);
  }

  async rescheduleAppointment(appointmentId: string, newSlotId: string): Promise<Appointment> {
    const res = await this.request<any>(`/appointments/${appointmentId}/reschedule`, {
      method: 'POST',
      body: JSON.stringify({ newSlotId })
    });
    return this.mapAppointment(res);
  }

  // --- Billing ---
  async getPaymentOrder(appointmentId: string): Promise<PaymentOrder> {
    return this.request<PaymentOrder>(`/billing/orders/${appointmentId}`);
  }

  async mockPay(paymentCode: string): Promise<{ success: boolean; message: string }> {
    return this.request<{ success: boolean; message: string }>('/billing/mock-pay', {
      method: 'POST',
      body: JSON.stringify({ paymentCode })
    });
  }

  // --- Encounters & Clinical Reports ---
  private mapEncounter(appointmentId: string, response: Partial<EncounterSummary> | null): EncounterSummary {
    return {
      ...response,
      appointmentId: response?.appointmentId || appointmentId,
      patientId: response?.patientId || '',
      doctorId: response?.doctorId || '',
      // Clinical service uses state, while the UI consumes a boolean.
      published: response?.state
        ? response.state.toUpperCase() === 'PUBLISHED'
        : response?.published === true,
      files: Array.isArray(response?.files) ? response.files : []
    };
  }

  async startEncounter(appointmentId: string): Promise<{ appointmentId: string; status: string; roomId: string }> {
    return this.request<{ appointmentId: string; status: string; roomId: string }>(
      `/appointments/${appointmentId}/start`,
      { method: 'POST' }
    );
  }

  async uploadReportFile(appointmentId: string, file: File): Promise<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.request<any>(`/clinical/encounters/${appointmentId}/reports`, {
      method: 'POST',
      body: formData
    });
  }

  async publishReports(appointmentId: string): Promise<EncounterSummary> {
    const response = await this.request<Partial<EncounterSummary>>(`/clinical/encounters/${appointmentId}/publish`, {
      method: 'POST'
    });
    return this.mapEncounter(appointmentId, response);
  }

  async completeEncounter(appointmentId: string): Promise<{ appointmentId: string; status: string }> {
    return this.request<{ appointmentId: string; status: string }>(
      `/appointments/${appointmentId}/complete`,
      { method: 'POST' }
    );
  }

  async getEncounter(appointmentId: string): Promise<EncounterSummary> {
    const response = await this.request<Partial<EncounterSummary> | null>(`/clinical/encounters/${appointmentId}`);
    return this.mapEncounter(appointmentId, response);
  }

  async downloadReportFile(appointmentId: string, fileId: string, fileName: string): Promise<void> {
    const response = await fetch(`${BASE_URL}/clinical/encounters/${appointmentId}/reports/${fileId}`, {
      headers: {
        Authorization: `Bearer ${this.token}`
      }
    });
    if (!response.ok) {
      throw new Error('Không thể tải tệp báo cáo');
    }
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  }

  // --- Realtime & WebRTC ---
  async getRoomToken(roomId: string): Promise<{ token: string; roomId: string; role: string }> {
    return this.request<{ token: string; roomId: string; role: string }>(`/rooms/${roomId}/token`, {
      method: 'POST'
    });
  }

  async getIceServers(): Promise<{ iceServers: RTCIceServer[] }> {
    return this.request<{ iceServers: RTCIceServer[] }>('/rooms/ice-servers');
  }

  async getRoomMessages(roomId: string): Promise<ChatMessage[]> {
    return this.request<ChatMessage[]>(`/rooms/${roomId}/messages`);
  }

  // --- Notifications ---
  async getEmailLogs(): Promise<EmailLog[]> {
    return this.request<EmailLog[]>('/notifications/logs');
  }
}

export const api = new ApiService();
