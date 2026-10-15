import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import { Appointment } from '../types';
import { mergeChatMessages, normalizeChatMessage, UiChatMessage } from '../utils/chatMessages';
import { Video, VideoOff, Mic, MicOff, Send, PhoneOff, Users, AlertCircle, MessageSquare } from 'lucide-react';

interface ConsultationRoomProps {
  appointmentId: string;
  onLeave: (message?: string) => void;
}

const COMPLETED_MESSAGE = 'Ca khám đã hoàn tất. Bạn có thể xem và tải báo cáo trong lịch khám.';

export const ConsultationRoom: React.FC<ConsultationRoomProps> = ({ appointmentId, onLeave }) => {
  const { user } = useAuth();
  const [appointment, setAppointment] = useState<Appointment | null>(null);

  // WebRTC & Media States
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [videoEnabled, setVideoEnabled] = useState<boolean>(true);
  const [audioEnabled, setAudioEnabled] = useState<boolean>(true);
  const [peerConnected, setPeerConnected] = useState<boolean>(false);
  const [peerJoined, setPeerJoined] = useState<boolean>(false);

  // Chat States
  const [messages, setMessages] = useState<UiChatMessage[]>([]);
  const [inputMessage, setInputMessage] = useState<string>('');
  const [wsConnected, setWsConnected] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteVideoRef = useRef<HTMLVideoElement | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const pcRef = useRef<RTCPeerConnection | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const chatBottomRef = useRef<HTMLDivElement | null>(null);
  const roomIdRef = useRef<string | null>(null);
  const historySyncRef = useRef<(() => Promise<void>) | null>(null);
  const pendingMessageTimersRef = useRef<Map<string, number>>(new Map());
  const roomClosedRef = useRef(false);
  const onLeaveRef = useRef(onLeave);
  const exitRoomRef = useRef<(message?: string) => void>(() => {});
  onLeaveRef.current = onLeave;

  // 1. Fetch Appointment and Room Token on Mount
  useEffect(() => {
    let isMounted = true;
    let historyPollId: number | null = null;
    let appointmentPollId: number | null = null;
    let reconnectTimerId: number | null = null;
    let reconnectAttempt = 0;
    roomClosedRef.current = false;
    const isActive = () => isMounted && !roomClosedRef.current;

    const releaseRoom = () => {
      if (historyPollId !== null) window.clearInterval(historyPollId);
      if (appointmentPollId !== null) window.clearInterval(appointmentPollId);
      if (reconnectTimerId !== null) window.clearTimeout(reconnectTimerId);
      historySyncRef.current = null;
      roomIdRef.current = null;
      pendingMessageTimersRef.current.forEach(timer => window.clearTimeout(timer));
      pendingMessageTimersRef.current.clear();
      localStreamRef.current?.getTracks().forEach(track => track.stop());
      localStreamRef.current = null;
      for (const video of [localVideoRef.current, remoteVideoRef.current]) {
        const media = video?.srcObject as MediaStream | null;
        media?.getTracks().forEach(track => track.stop());
        if (video) video.srcObject = null;
      }
      const pc = pcRef.current;
      pcRef.current = null;
      if (pc) {
        pc.ontrack = null;
        pc.onicecandidate = null;
        pc.onconnectionstatechange = null;
        pc.close();
      }
      const ws = wsRef.current;
      wsRef.current = null;
      if (ws) {
        ws.onopen = null;
        ws.onmessage = null;
        ws.onclose = null;
        ws.onerror = null;
        ws.close();
      }
    };

    exitRoomRef.current = message => {
      if (!isActive()) return;
      roomClosedRef.current = true;
      releaseRoom();
      setWsConnected(false);
      setLocalStream(null);
      setRemoteStream(null);
      setPeerConnected(false);
      onLeaveRef.current(message);
    };

    // This watcher only exists inside the room. A patient who has already
    // left must never be redirected by a later completion event.
    let appointmentSyncInFlight = false;
    const syncAppointment = async () => {
      if (!isActive() || appointmentSyncInFlight) return;
      appointmentSyncInFlight = true;
      try {
        const latest = await api.getAppointment(appointmentId);
        if (!isActive()) return;
        setAppointment(latest);
        if (latest.status === 'COMPLETED') exitRoomRef.current(COMPLETED_MESSAGE);
      } catch (statusError) {
        console.warn('Could not synchronize appointment status:', statusError);
      } finally {
        appointmentSyncInFlight = false;
      }
    };

    async function initRoom() {
      try {
        const appt = await api.getAppointment(appointmentId);
        if (!isActive()) return;
        setAppointment(appt);
        if (appt.status === 'COMPLETED') {
          exitRoomRef.current(COMPLETED_MESSAGE);
          return;
        }
        appointmentPollId = window.setInterval(() => void syncAppointment(), 3_000);

        // Fetch Room Token
        const tokenData = await api.getRoomToken(appointmentId);
        if (!isActive()) return;
        const { token, roomId } = tokenData;
        roomIdRef.current = roomId;

        // Fetch Ice Servers
        const iceData = await api.getIceServers();
        if (!isActive()) return;
        const iceServers = iceData.iceServers;

        // WebSocket is the primary transport. Periodic history sync is a
        // safety net for a missed broadcast/proxy interruption and also
        // reconciles optimistic messages with their persisted server copy.
        let historySyncInFlight = false;
        const syncHistory = async () => {
          if (!isActive() || historySyncInFlight) return;
          historySyncInFlight = true;
          try {
            const history = await api.getRoomMessages(roomId);
            if (!isActive()) return;
            const normalizedHistory = (Array.isArray(history) ? history : [])
              .map(normalizeChatMessage)
              .filter((message): message is UiChatMessage => message !== null);
            normalizedHistory.forEach(message => {
              const timer = pendingMessageTimersRef.current.get(message.clientMessageId);
              if (timer !== undefined) {
                window.clearTimeout(timer);
                pendingMessageTimersRef.current.delete(message.clientMessageId);
              }
            });
            setMessages(previous => mergeChatMessages(previous, normalizedHistory));
          } catch (historyError) {
            console.warn('Could not synchronize chat history:', historyError);
          } finally {
            historySyncInFlight = false;
          }
        };
        historySyncRef.current = syncHistory;
        await syncHistory();
        if (!isActive()) return;
        historyPollId = window.setInterval(() => void syncHistory(), 3_000);

        // Acquire local media stream
        let stream: MediaStream | null = null;
        try {
          stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
        } catch (mediaErr) {
          if (!isActive()) return;
          console.warn('Could not get video/audio, trying audio only:', mediaErr);
          try {
            stream = await navigator.mediaDevices.getUserMedia({ audio: true });
          } catch (audioErr) {
            console.warn('No media devices available:', audioErr);
          }
        }

        if (!isActive()) {
          stream?.getTracks().forEach(track => track.stop());
          return;
        }

        if (stream) {
          localStreamRef.current = stream;
          setLocalStream(stream);
          if (localVideoRef.current) {
            localVideoRef.current.srcObject = stream;
          }
        }

        // Initialize PeerConnection
        const pc = new RTCPeerConnection({ iceServers });
        pcRef.current = pc;

        if (stream) {
          stream.getTracks().forEach(track => pc.addTrack(track, stream!));
        }

        pc.ontrack = (event) => {
          if (event.streams && event.streams[0]) {
            setRemoteStream(event.streams[0]);
            if (remoteVideoRef.current) {
              remoteVideoRef.current.srcObject = event.streams[0];
            }
          }
        };

        pc.onconnectionstatechange = () => {
          setPeerConnected(pc.connectionState === 'connected');
        };

        pc.onicecandidate = (event) => {
          if (event.candidate && wsRef.current?.readyState === WebSocket.OPEN) {
            wsRef.current.send(JSON.stringify({
              type: 'SIGNAL',
              signalType: 'ICE_CANDIDATE',
              payload: event.candidate
            }));
          }
        };

        // Initialize WebSocket and reconnect automatically if the proxy/server
        // closes it after persisting a message but before broadcasting the ACK.
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws?token=${token}`;
        const connectWebSocket = () => {
          if (!isActive()) return;
          try {
            const ws = new WebSocket(wsUrl);
            wsRef.current = ws;

            ws.onopen = () => {
              if (!isActive() || wsRef.current !== ws) return;
              reconnectAttempt = 0;
              ws.send(JSON.stringify({ type: 'JOIN', roomId, token }));
            };

            ws.onmessage = async (evt) => {
              if (!isActive() || wsRef.current !== ws) return;
              try {
                const data = JSON.parse(evt.data);
                await handleWsMessage(data, pc);
              } catch (messageError) {
                console.error('Failed to parse WS msg:', messageError);
                if (isActive()) setError('Không thể xử lý dữ liệu từ phòng khám trực tuyến.');
              }
            };

            ws.onclose = () => {
              if (!isActive() || wsRef.current !== ws) return;
              setWsConnected(false);
              void syncAppointment();
              setError('Mất kết nối phòng khám. Hệ thống đang tự kết nối lại...');
              const retryDelay = Math.min(1_000 * (2 ** reconnectAttempt), 5_000);
              reconnectAttempt += 1;
              reconnectTimerId = window.setTimeout(connectWebSocket, retryDelay);
            };

            ws.onerror = () => {
              if (isActive() && wsRef.current === ws) {
                setError('Kết nối phòng khám không ổn định. Hệ thống đang thử kết nối lại...');
              }
            };
          } catch (socketError) {
            console.error('Could not create WebSocket:', socketError);
            reconnectTimerId = window.setTimeout(connectWebSocket, 2_000);
          }
        };
        connectWebSocket();

      } catch (err: any) {
        // Completion may race token issuance. Recheck before showing a stale
        // join error or leaving the camera active while the room is closed.
        await syncAppointment();
        if (isActive()) setError(err.message || 'Không thể tham gia phòng khám');
      }
    }

    initRoom();

    return () => {
      isMounted = false;
      roomClosedRef.current = true;
      releaseRoom();
    };
  }, [appointmentId]);

  const handleLeaveRoom = () => {
    exitRoomRef.current();
  };

  // Handle incoming signaling & chat messages
  const handleWsMessage = async (data: any, pc: RTCPeerConnection) => {
    const nestedEventType = typeof data?.event === 'string' ? data.event : data?.event?.type;
    const eventType = String(
      data?.type || data?.eventType || nestedEventType || data?.payload?.type || data?.data?.type || ''
    ).toUpperCase();
    switch (eventType) {
      case 'ROOM_CLOSED':
        exitRoomRef.current(data.reason === 'COMPLETED' ? COMPLETED_MESSAGE : (data.message || 'Phòng khám đã đóng.'));
        break;

      case 'JOINED':
        setWsConnected(true);
        setError(null);
        break;

      case 'USER_JOINED':
        setPeerJoined(true);
        break;

      case 'PEER_JOINED': {
        setPeerJoined(true);
        // The participant already in the room initiates the WebRTC connection.
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        wsRef.current?.send(JSON.stringify({
          type: 'SIGNAL',
          signalType: 'OFFER',
          payload: offer
        }));
        break;
      }

      case 'SIGNAL': {
        const { signalType, payload } = data;
        if (signalType === 'OFFER') {
          setPeerJoined(true);
          await pc.setRemoteDescription(new RTCSessionDescription(payload));
          const answer = await pc.createAnswer();
          await pc.setLocalDescription(answer);
          wsRef.current?.send(JSON.stringify({
            type: 'SIGNAL',
            signalType: 'ANSWER',
            payload: answer
          }));
        } else if (signalType === 'ANSWER') {
          await pc.setRemoteDescription(new RTCSessionDescription(payload));
        } else if (signalType === 'ICE_CANDIDATE' && payload) {
          try {
            await pc.addIceCandidate(new RTCIceCandidate(payload));
          } catch (e) {
            console.warn('Error adding ice candidate:', e);
          }
        }
        break;
      }

      case 'CHAT': {
        const message = normalizeChatMessage(data);
        if (!message) {
          // Never append malformed CHAT events. Previously their missing ID
          // made every later event look like the same message (`undefined`).
          console.warn('Ignored malformed/empty CHAT event:', data);
          break;
        }
        const timer = pendingMessageTimersRef.current.get(message.clientMessageId);
        if (timer !== undefined) {
          window.clearTimeout(timer);
          pendingMessageTimersRef.current.delete(message.clientMessageId);
        }
        setMessages(previous => mergeChatMessages(previous, [message]));
        break;
      }

      case 'USER_LEFT':
      case 'PEER_LEFT':
        setPeerJoined(false);
        setPeerConnected(false);
        break;

      case 'ERROR':
        setError(data.message || 'Phòng khám trực tuyến gặp lỗi.');
        break;
    }
  };

  // Scroll chat to bottom on new messages
  useEffect(() => {
    chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Media Controls
  const toggleVideo = () => {
    if (localStream) {
      localStream.getVideoTracks().forEach(t => {
        t.enabled = !videoEnabled;
      });
      setVideoEnabled(!videoEnabled);
    }
  };

  const toggleAudio = () => {
    if (localStream) {
      localStream.getAudioTracks().forEach(t => {
        t.enabled = !audioEnabled;
      });
      setAudioEnabled(!audioEnabled);
    }
  };

  // Send Chat Message
  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (roomClosedRef.current) return;
    const body = inputMessage.trim();
    if (!body) return;
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      setError('Chưa kết nối được phòng khám nên chưa thể gửi tin nhắn.');
      return;
    }

    const clientMsgId = typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, char => {
          const random = Math.floor(Math.random() * 16);
          const value = char === 'x' ? random : (random & 0x3) | 0x8;
          return value.toString(16);
        });
    const payload = {
      type: 'CHAT',
      clientMessageId: clientMsgId,
      body,
      senderName: user?.fullName || user?.displayName || user?.email
    };

    const optimisticMessage = normalizeChatMessage({
      id: `pending-${clientMsgId}`,
      roomId: roomIdRef.current || undefined,
      clientMessageId: clientMsgId,
      senderId: user?.id || '',
      senderRole: user?.role,
      senderName: user?.fullName || user?.displayName || user?.email || 'Bạn',
      body,
      createdAt: new Date().toISOString(),
      deliveryStatus: 'sending'
    });

    if (optimisticMessage) {
      setMessages(previous => mergeChatMessages(previous, [optimisticMessage]));
    }

    try {
      wsRef.current.send(JSON.stringify(payload));
      setInputMessage('');
      setError(null);

      // Most acknowledgements arrive immediately. If one is missed, fetch
      // persisted history; only mark failed when neither path confirms it.
      window.setTimeout(() => void historySyncRef.current?.(), 700);
      const failureTimer = window.setTimeout(() => {
        pendingMessageTimersRef.current.delete(clientMsgId);
        setMessages(previous => previous.map(message =>
          message.clientMessageId === clientMsgId && message.deliveryStatus === 'sending'
            ? { ...message, deliveryStatus: 'failed' }
            : message
        ));
        void historySyncRef.current?.();
      }, 8_000);
      pendingMessageTimersRef.current.set(clientMsgId, failureTimer);
    } catch {
      setMessages(previous => previous.map(message =>
        message.clientMessageId === clientMsgId
          ? { ...message, deliveryStatus: 'failed' }
          : message
      ));
      setInputMessage(body);
      setError('Không thể gửi tin nhắn. Vui lòng kiểm tra kết nối và thử lại.');
    }
  };

  const isCompleted = appointment?.status === 'COMPLETED';

  return (
    <div>
      {/* Header bar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
        <div>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 800, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Video color="var(--primary)" size={24} />
            Phòng khám trực tuyến #{appointmentId.substring(0, 8)}
          </h1>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
              <Users size={14} />
              {peerJoined ? (
                <span style={{ color: 'var(--success)', fontWeight: 600 }}>Đối phương đã vào phòng</span>
              ) : (
                <span style={{ color: 'var(--warning)', fontWeight: 600 }}>Đang chờ đối phương vào phòng...</span>
              )}
            </span>
            <span>•</span>
            <span>{wsConnected ? 'WebSocket kết nối ổn định' : 'Đang kết nối lại...'}</span>
          </div>
        </div>

        <button
          id="leave-room-btn"
          className="btn btn-danger"
          onClick={handleLeaveRoom}
        >
          <PhoneOff size={16} />
          Rời phòng khám
        </button>
      </div>

      {error && (
        <div className="alert alert-danger">
          <AlertCircle size={20} />
          <div>{error}</div>
        </div>
      )}

      {/* Main room layout: Left Video grid, Right Chat */}
      <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: '1.25rem' }}>
        {/* Left Column: Video Feeds & Controls */}
        <div>
          <div className="video-grid">
            {/* Remote Video Box */}
            <div className="video-box" style={{ background: '#090d16' }}>
              <video
                ref={remoteVideoRef}
                autoPlay
                playsInline
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
              <div className="video-label">
                {user?.role === 'PATIENT' ? 'Bác sĩ phụ trách' : 'Bệnh nhân'}
                {!peerConnected && ' (Chưa kết nối)'}
              </div>
            </div>

            {/* Local Video Box */}
            <div className="video-box" style={{ background: '#090d16' }}>
              <video
                ref={localVideoRef}
                autoPlay
                playsInline
                muted
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
              <div className="video-label">Bạn ({user?.fullName || user?.displayName || user?.email})</div>
            </div>
          </div>

          {/* Video Controls Bar */}
          <div className="card" style={{ display: 'flex', justifyContent: 'center', gap: '1rem', padding: '0.85rem' }}>
            <button
              id="toggle-mic-btn"
              className={`btn ${audioEnabled ? 'btn-secondary' : 'btn-danger'}`}
              onClick={toggleAudio}
              title={audioEnabled ? 'Tắt Micro' : 'Bật Micro'}
            >
              {audioEnabled ? <Mic size={18} /> : <MicOff size={18} />}
              {audioEnabled ? 'Tắt Micro' : 'Bật Micro'}
            </button>

            <button
              id="toggle-video-btn"
              className={`btn ${videoEnabled ? 'btn-secondary' : 'btn-danger'}`}
              onClick={toggleVideo}
              title={videoEnabled ? 'Tắt Camera' : 'Bật Camera'}
            >
              {videoEnabled ? <Video size={18} /> : <VideoOff size={18} />}
              {videoEnabled ? 'Tắt Camera' : 'Bật Camera'}
            </button>
          </div>
        </div>

        {/* Right Column: Encrypted Chat Panel */}
        <div className="card" style={{ padding: '1rem', display: 'flex', flexDirection: 'column', height: '560px' }}>
          <div className="card-header" style={{ marginBottom: '0.75rem', paddingBottom: '0.5rem' }}>
            <h3 className="card-title" style={{ fontSize: '1rem' }}>
              <MessageSquare size={16} color="var(--primary)" />
              Trao đổi trực tiếp (Mã hóa đầu cuối)
            </h3>
            {isCompleted && <span className="badge badge-completed">Chỉ đọc</span>}
          </div>

          {/* Messages list */}
          <div className="chat-messages" style={{ flex: 1, overflowY: 'auto' }}>
            {messages.length === 0 ? (
              <div style={{ textAlign: 'center', color: 'var(--text-muted)', margin: 'auto', fontSize: '0.85rem' }}>
                Chưa có tin nhắn nào. Hãy bắt đầu cuộc trò chuyện.
              </div>
            ) : (
              messages.map(msg => {
                const isMine = msg.senderId === user?.id;
                const otherRole = user?.role === 'PATIENT' ? 'DOCTOR' : 'PATIENT';
                const senderRole = msg.senderRole || (isMine ? user?.role : otherRole);
                const roleLabel = senderRole === 'DOCTOR' ? 'Bác sĩ' : senderRole === 'PATIENT' ? 'Bệnh nhân' : 'Người dùng';
                const senderName = msg.senderName || (isMine ? (user?.fullName || user?.displayName || 'Bạn') : roleLabel);
                const content = msg.body ?? msg.content ?? '';
                const senderLabel = senderName.toLocaleLowerCase() === roleLabel.toLocaleLowerCase()
                  ? roleLabel
                  : `${senderName} (${roleLabel})`;
                return (
                  <div key={msg.clientMessageId || msg.id} className={`chat-bubble ${isMine ? 'mine' : 'theirs'}`}>
                    <div className="chat-sender">{senderLabel}</div>
                    <div>{content}</div>
                    <div className="chat-time">
                      {new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      {msg.deliveryStatus === 'sending' && ' · Đang gửi'}
                      {msg.deliveryStatus === 'failed' && ' · Chưa gửi được'}
                    </div>
                  </div>
                );
              })
            )}
            <div ref={chatBottomRef} />
          </div>

          {/* Chat Input */}
          <form onSubmit={handleSendMessage} className="chat-input-bar">
            <input
              id="chat-message-input"
              type="text"
              placeholder={isCompleted
                ? 'Ca khám đã hoàn tất. Lịch sử chat chỉ đọc.'
                : wsConnected
                  ? 'Nhập tin nhắn cho bác sĩ/bệnh nhân...'
                  : 'Đang kết nối phòng khám...'}
              value={inputMessage}
              disabled={isCompleted || !wsConnected}
              onChange={e => setInputMessage(e.target.value)}
            />
            <button
              id="send-chat-btn"
              type="submit"
              className="btn btn-primary"
              disabled={isCompleted || !wsConnected || !inputMessage.trim()}
            >
              <Send size={16} />
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};
