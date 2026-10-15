import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { LogIn, UserPlus, Shield, User, AlertCircle, Sparkles } from 'lucide-react';

export const AuthModal: React.FC = () => {
  const { login, register, switchDemoUser } = useAuth();
  const [isRegister, setIsRegister] = useState<boolean>(false);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      if (isRegister) {
        await register({ email, password, fullName, phone, role: 'PATIENT' });
      } else {
        await login(email, password);
      }
    } catch (err: any) {
      setError(err.message || 'Xác thực thất bại');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: '80vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1.5rem' }}>
      <div className="card" style={{ maxWidth: '440px', width: '100%', padding: '2rem' }}>
        <div style={{ textAlign: 'center', marginBottom: '1.5rem' }}>
          <div style={{ width: '48px', height: '48px', margin: '0 auto 0.75rem', background: 'var(--primary)', borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', boxShadow: '0 4px 12px var(--primary-glow)' }}>
            <Sparkles size={24} />
          </div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 800 }}>
            {isRegister ? 'Đăng ký Tài khoản Bệnh nhân' : 'Đăng nhập Hệ thống Telehealth'}
          </h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginTop: '0.25rem' }}>
            Nền tảng Quản lý Bệnh viện & Tư vấn Khám chữa bệnh Trực tuyến
          </p>
        </div>

        {/* Demo Fast Login Box */}
        <div style={{ background: 'var(--bg-subtle)', padding: '0.85rem', borderRadius: 'var(--radius-md)', marginBottom: '1.5rem' }}>
          <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '0.5rem', textAlign: 'center' }}>
            1-Click Đăng nhập Demo
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.5rem' }}>
            <button
              type="button"
              id="quick-demo-admin"
              className="btn btn-secondary"
              style={{ fontSize: '0.75rem', padding: '0.4rem 0.6rem' }}
              onClick={() => switchDemoUser('ADMIN')}
            >
              Admin Quản trị
            </button>
            <button
              type="button"
              id="quick-demo-doctor"
              className="btn btn-secondary"
              style={{ fontSize: '0.75rem', padding: '0.4rem 0.6rem' }}
              onClick={() => switchDemoUser('DOCTOR')}
            >
              BS. Ánh (Tim mạch)
            </button>
            <button
              type="button"
              id="quick-demo-doctor2"
              className="btn btn-secondary"
              style={{ fontSize: '0.75rem', padding: '0.4rem 0.6rem' }}
              onClick={() => switchDemoUser('DOCTOR_2')}
            >
              BS. Bình (Nhi khoa)
            </button>
            <button
              type="button"
              id="quick-demo-patient1"
              className="btn btn-secondary"
              style={{ fontSize: '0.75rem', padding: '0.4rem 0.6rem' }}
              onClick={() => switchDemoUser('PATIENT_1')}
            >
              Bệnh nhân 1 (Minh)
            </button>
            <button
              type="button"
              id="quick-demo-patient2"
              className="btn btn-secondary"
              style={{ fontSize: '0.75rem', padding: '0.4rem 0.6rem' }}
              onClick={() => switchDemoUser('PATIENT_2')}
            >
              Bệnh nhân 2 (Hương)
            </button>
          </div>
        </div>

        {error && (
          <div className="alert alert-danger" style={{ fontSize: '0.85rem' }}>
            <AlertCircle size={16} />
            <div>{error}</div>
          </div>
        )}

        {/* Manual form */}
        <form onSubmit={handleSubmit}>
          {isRegister && (
            <>
              <div className="form-group">
                <label className="form-label">Họ và tên</label>
                <input
                  id="reg-fullname"
                  type="text"
                  required
                  placeholder="Nguyễn Văn A"
                  value={fullName}
                  onChange={e => setFullName(e.target.value)}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Số điện thoại</label>
                <input
                  id="reg-phone"
                  type="tel"
                  placeholder="0912345678"
                  value={phone}
                  onChange={e => setPhone(e.target.value)}
                />
              </div>
            </>
          )}

          <div className="form-group">
            <label className="form-label">Email</label>
            <input
              id="auth-email-input"
              type="email"
              required
              placeholder="patient1@demo.com"
              value={email}
              onChange={e => setEmail(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label className="form-label">Mật khẩu</label>
            <input
              id="auth-password-input"
              type="password"
              required
              placeholder="••••••••"
              value={password}
              onChange={e => setPassword(e.target.value)}
            />
          </div>

          <button
            id="auth-submit-btn"
            type="submit"
            className="btn btn-primary"
            style={{ width: '100%', marginTop: '0.5rem' }}
            disabled={loading}
          >
            {loading ? 'Đang xử lý...' : isRegister ? 'Đăng ký tài khoản' : 'Đăng nhập'}
          </button>
        </form>

        <div style={{ textAlign: 'center', marginTop: '1.25rem', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
          {isRegister ? (
            <span>
              Đã có tài khoản?{' '}
              <button
                type="button"
                style={{ color: 'var(--primary)', background: 'none', fontWeight: 600 }}
                onClick={() => setIsRegister(false)}
              >
                Đăng nhập ngay
              </button>
            </span>
          ) : (
            <span>
              Chưa có tài khoản?{' '}
              <button
                type="button"
                style={{ color: 'var(--primary)', background: 'none', fontWeight: 600 }}
                onClick={() => setIsRegister(true)}
              >
                Đăng ký tài khoản bệnh nhân
              </button>
            </span>
          )}
        </div>
      </div>
    </div>
  );
};
