export type Role = 'PATIENT' | 'DOCTOR' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  displayName?: string;
  fullName?: string;
  role: Role;
  phone?: string;
  token?: string;
}

export interface Specialty {
  id: string;
  code: string;
  name: string;
  description?: string;
}

export interface Doctor {
  id: string;
  userId: string;
  displayName?: string;
  fullName: string;
  specialtyId: string;
  specialtyName?: string;
  licenseNumber?: string;
  experienceYears?: number;
  bio?: string;
}

export interface Slot {
  id: string;
  doctorId: string;
  doctorName?: string;
  specialtyName?: string;
  startsAt?: string;
  slotDate: string;
  startTime: string;
  endTime: string;
  available?: boolean;
  status?: string;
  version?: number;
}

export type AppointmentStatus = 
  | 'PENDING_PAYMENT' 
  | 'CONFIRMED' 
  | 'IN_PROGRESS' 
  | 'COMPLETED' 
  | 'EXPIRED' 
  | 'CANCELLED';

export interface Appointment {
  id: string;
  patientId: string;
  doctorId: string;
  doctorName?: string;
  specialtyName?: string;
  slotId: string;
  startsAt?: string;
  slotDate: string;
  startTime: string;
  endTime: string;
  status: AppointmentStatus;
  intakeId?: string;
  expiresAt?: string;
  paymentExpiresAt?: string;
  amountVnd?: number;
  totalFee: number;
  canReschedule?: boolean;
  version: number;
  createdAt: string;
  demo?: boolean;
}

export interface IntakeForm {
  reason: string;
  symptoms: string;
  medicalHistory?: string;
  underlyingDiseases?: string;
  underlyingConditions?: string;
  symptomOnset?: string;
  symptomStartedAt?: string;
  currentMedications?: string;
  drugAllergies?: string;
}

export interface PaymentOrder {
  id: string;
  appointmentId: string;
  patientId?: string;
  amount?: number;
  amountVnd?: number;
  currency?: string;
  paymentCode?: string;
  status: 'PENDING' | 'SUCCESS' | 'EXPIRED' | 'FAILED';
  expiresAt: string;
}

export interface ChatMessage {
  id: string | number;
  roomId?: string;
  clientMessageId: string;
  senderId: string;
  senderRole?: Role;
  senderName?: string;
  /** Current realtime API field. */
  body?: string;
  /** Kept for compatibility with older realtime payloads. */
  content?: string;
  createdAt: string;
}

export interface ReportFile {
  id: string;
  originalName?: string;
  fileName?: string;
  sizeBytes?: number;
  fileSizeBytes?: number;
  mimeType?: string;
  contentType?: string;
  state?: string;
  createdAt: string;
  downloadUrl?: string;
}

export interface EncounterSummary {
  appointmentId: string;
  patientId: string;
  doctorId: string;
  state?: string;
  published: boolean;
  publishedAt?: string;
  files: ReportFile[];
}

export interface EmailLog {
  id: string;
  recipientEmail: string;
  subject: string;
  status: 'PENDING' | 'SENT' | 'FAILED';
  retryCount: number;
  lastError?: string;
  createdAt: string;
  sentAt?: string;
}
