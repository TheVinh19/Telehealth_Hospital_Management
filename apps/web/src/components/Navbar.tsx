import React from 'react';
import { useAuth } from '../context/AuthContext';
import { Activity, Calendar, Clock, UserCheck, ShieldCheck, LogOut, Video } from 'lucide-react';

interface NavbarProps {
  currentTab: string;
  setCurrentTab: (tab: string) => void;
}

export const Navbar: React.FC<NavbarProps> = ({ currentTab, setCurrentTab }) => {
  const { user, switchDemoUser, logout } = useAuth();

  return (
    <nav className="navbar">
      <div className="navbar-inner">
        <div className="brand">
          <div className="brand-icon">
            <Activity size={22} />
          </div>
          <div>
            <span>Telehealth</span>
            <span style={{ color: 'var(--primary)', marginLeft: '4px' }}>Hospital</span>
          </div>
        </div>

        {/* Navigation Tabs based on role */}
        <div className="nav-links">
          {user && user.role === 'PATIENT' && (
            <>
              <button
                id="nav-booking-btn"
                className={`nav-btn ${currentTab === 'booking' ? 'active' : ''}`}
                onClick={() => setCurrentTab('booking')}
              >
                <Calendar size={16} />
                <span>Đặt khám mới</span>
              </button>
              <button
                id="nav-my-appointments-btn"
                className={`nav-btn ${currentTab === 'appointments' ? 'active' : ''}`}
                onClick={() => setCurrentTab('appointments')}
              >
                <Clock size={16} />
                <span>Lịch khám của tôi</span>
              </button>
            </>
          )}

          {user && user.role === 'DOCTOR' && (
            <button
              id="nav-doctor-schedule-btn"
              className={`nav-btn ${currentTab === 'doctor-schedule' ? 'active' : ''}`}
              onClick={() => setCurrentTab('doctor-schedule')}
            >
              <Calendar size={16} />
              <span>Lịch khám được giao</span>
            </button>
          )}

          {user && user.role === 'ADMIN' && (
            <>
              <button
                id="nav-admin-manage-btn"
                className={`nav-btn ${currentTab === 'admin-manage' ? 'active' : ''}`}
                onClick={() => setCurrentTab('admin-manage')}
              >
                <ShieldCheck size={16} />
                <span>Quản trị Bác sĩ & Ca</span>
              </button>
              <button
                id="nav-admin-logs-btn"
                className={`nav-btn ${currentTab === 'admin-logs' ? 'active' : ''}`}
                onClick={() => setCurrentTab('admin-logs')}
              >
                <Clock size={16} />
                <span>Nhật ký Email & Vận hành</span>
              </button>
            </>
          )}
        </div>

        {/* Demo User Switcher & User Profile */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div className="role-switcher" title="Chuyển nhanh tài khoản demo">
            <button
              id="demo-admin-btn"
              className={`role-pill ${user?.email === 'admin@demo.com' ? 'active' : ''}`}
              onClick={() => switchDemoUser('ADMIN')}
            >
              Admin
            </button>
            <button
              id="demo-doctor-btn"
              className={`role-pill ${user?.email === 'doctor.anh@demo.com' ? 'active' : ''}`}
              onClick={() => switchDemoUser('DOCTOR')}
            >
              BS Ánh
            </button>
            <button
              id="demo-doctor2-btn"
              className={`role-pill ${user?.email === 'doctor.binh@demo.com' ? 'active' : ''}`}
              onClick={() => switchDemoUser('DOCTOR_2')}
            >
              BS Bình
            </button>
            <button
              id="demo-patient1-btn"
              className={`role-pill ${user?.email === 'patient1@demo.com' ? 'active' : ''}`}
              onClick={() => switchDemoUser('PATIENT_1')}
            >
              Bệnh nhân 1
            </button>
            <button
              id="demo-patient2-btn"
              className={`role-pill ${user?.email === 'patient2@demo.com' ? 'active' : ''}`}
              onClick={() => switchDemoUser('PATIENT_2')}
            >
              Bệnh nhân 2
            </button>
          </div>

          {user && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <div style={{ fontSize: '0.85rem', textAlign: 'right' }}>
                <div style={{ fontWeight: 600 }}>{user.fullName || user.displayName || user.email}</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{user.role}</div>
              </div>
              <button
                id="logout-btn"
                onClick={logout}
                className="btn btn-secondary"
                style={{ padding: '0.45rem 0.65rem' }}
                title="Đăng xuất"
              >
                <LogOut size={16} />
              </button>
            </div>
          )}
        </div>
      </div>
    </nav>
  );
};
