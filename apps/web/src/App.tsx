import React, { useState } from 'react';
import { useAuth } from './context/AuthContext';
import { Navbar } from './components/Navbar';
import { AuthModal } from './components/AuthModal';
import { BookingFlow } from './components/BookingFlow';
import { MyAppointments } from './components/MyAppointments';
import { DoctorDashboard } from './components/DoctorDashboard';
import { AdminDashboard } from './components/AdminDashboard';
import { ConsultationRoom } from './components/ConsultationRoom';

export const App: React.FC = () => {
  const { user, loading } = useAuth();
  const [currentTab, setCurrentTab] = useState<string>('booking');
  const [activeRoomApptId, setActiveRoomApptId] = useState<string | null>(null);
  const [roomNotice, setRoomNotice] = useState<string | null>(null);

  const enterRoom = (appointmentId: string) => {
    setRoomNotice(null);
    setActiveRoomApptId(appointmentId);
  };

  const leaveRoom = (message?: string) => {
    setActiveRoomApptId(null);
    setRoomNotice(message || null);
    if (user?.role === 'PATIENT') setCurrentTab('appointments');
  };

  if (loading) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ color: 'var(--text-muted)', fontSize: '1.1rem' }}>Đang khởi tạo hệ thống Telehealth...</div>
      </div>
    );
  }

  if (!user) {
    return <AuthModal />;
  }

  // If user is currently in a consultation room
  if (activeRoomApptId) {
    return (
      <div className="app-container">
        <Navbar currentTab={currentTab} setCurrentTab={setCurrentTab} />
        <main className="main-content">
          <ConsultationRoom
            appointmentId={activeRoomApptId}
            onLeave={leaveRoom}
          />
        </main>
      </div>
    );
  }

  return (
    <div className="app-container">
      <Navbar currentTab={currentTab} setCurrentTab={setCurrentTab} />

      <main className="main-content">
        {roomNotice && (
          <div className="alert alert-success" role="status">
            <div>{roomNotice}</div>
            <button className="btn btn-secondary" onClick={() => setRoomNotice(null)} aria-label="Đóng thông báo">×</button>
          </div>
        )}

        {/* Patient Tabs */}
        {user.role === 'PATIENT' && (
          <>
            {currentTab === 'booking' && (
              <BookingFlow
                onSuccess={() => setCurrentTab('appointments')}
              />
            )}
            {currentTab === 'appointments' && (
              <MyAppointments
                onEnterRoom={enterRoom}
              />
            )}
          </>
        )}

        {/* Doctor Tabs */}
        {user.role === 'DOCTOR' && (
          <DoctorDashboard
            onEnterRoom={enterRoom}
          />
        )}

        {/* Admin Tabs */}
        {user.role === 'ADMIN' && (
          <AdminDashboard />
        )}
      </main>
    </div>
  );
};
