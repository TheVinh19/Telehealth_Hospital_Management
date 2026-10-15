import { ChatMessage, Role } from '../types';

export type ChatDeliveryStatus = 'sending' | 'sent' | 'failed';

export interface UiChatMessage extends ChatMessage {
  deliveryStatus?: ChatDeliveryStatus;
}

type UnknownRecord = Record<string, unknown>;

const NESTED_PAYLOAD_KEYS = ['message', 'payload', 'data', 'chatMessage', 'event'] as const;

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function parseNestedJson(value: string): unknown {
  const trimmed = value.trim();
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return value;

  try {
    return JSON.parse(trimmed);
  } catch {
    return value;
  }
}

/**
 * Collect the inner message first, then its envelopes.  Realtime deployments
 * have used flat CHAT events as well as message/payload/data wrappers.
 */
function collectPayloadRecords(value: unknown, seen = new Set<unknown>()): UnknownRecord[] {
  if (typeof value === 'string') {
    const parsed = parseNestedJson(value);
    return parsed === value ? [{ body: value }] : collectPayloadRecords(parsed, seen);
  }
  if (!isRecord(value) || seen.has(value)) return [];

  seen.add(value);
  const records: UnknownRecord[] = [];
  for (const key of NESTED_PAYLOAD_KEYS) {
    const nested = value[key];
    if (nested !== undefined && nested !== null) {
      // `event: "CHAT"` describes the envelope; it is not chat content.
      if (key === 'event' && typeof nested === 'string') continue;
      records.push(...collectPayloadRecords(nested, seen));
    }
  }
  records.push(value);
  return records;
}

function firstText(records: UnknownRecord[], keys: string[]): string | undefined {
  for (const record of records) {
    for (const key of keys) {
      const value = record[key];
      if (typeof value === 'string' && value.trim()) return value.trim();
      if (typeof value === 'number') return String(value);
    }
  }
  return undefined;
}

function normalizeRole(value?: string): Role | undefined {
  const role = value?.toUpperCase();
  return role === 'PATIENT' || role === 'DOCTOR' || role === 'ADMIN' ? role : undefined;
}

function normalizedTimestamp(value?: string): string {
  if (!value) return new Date().toISOString();
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? new Date().toISOString() : date.toISOString();
}

/**
 * Converts every known realtime/history payload shape to one safe UI model.
 * Empty payloads are deliberately rejected so they can never render as `()`.
 */
export function normalizeChatMessage(raw: unknown): UiChatMessage | null {
  const records = collectPayloadRecords(raw);
  if (records.length === 0) return null;

  const body = firstText(records, ['body', 'content', 'text', 'messageBody']);
  if (!body) return null;

  const serverId = firstText(records, ['id', 'messageId']);
  const senderId = firstText(records, ['senderId', 'userId', 'fromUserId']) || '';
  const createdAt = normalizedTimestamp(firstText(records, ['createdAt', 'sentAt', 'timestamp']));
  const explicitClientId = firstText(records, ['clientMessageId', 'clientMsgId', 'requestId']);
  // A CHAT frame without these identifiers cannot be attributed or safely
  // deduplicated. History polling will recover its persisted canonical copy.
  if (!explicitClientId || !senderId) return null;
  const clientMessageId = explicitClientId;
  const deliveryStatus = firstText(records, ['deliveryStatus']) as ChatDeliveryStatus | undefined;

  return {
    id: serverId || clientMessageId,
    roomId: firstText(records, ['roomId']),
    clientMessageId,
    senderId,
    senderRole: normalizeRole(firstText(records, ['senderRole', 'role'])),
    senderName: firstText(records, ['senderName', 'displayName', 'fullName']),
    body,
    content: body,
    createdAt,
    deliveryStatus: deliveryStatus === 'sending' || deliveryStatus === 'failed' ? deliveryStatus : 'sent'
  };
}

function samePersistedMessage(left: UiChatMessage, right: UiChatMessage): boolean {
  if (left.clientMessageId && right.clientMessageId && left.clientMessageId === right.clientMessageId) {
    return true;
  }
  if (left.id !== undefined && right.id !== undefined && String(left.id) === String(right.id)) {
    return true;
  }

  // Equal text is not a message identity: users can send "hi" twice before
  // either acknowledgement arrives. Both messages must remain visible.
  return false;
}

export function mergeChatMessages(current: UiChatMessage[], incoming: unknown[]): UiChatMessage[] {
  const merged = [...current];

  for (const raw of incoming) {
    const message = normalizeChatMessage(raw);
    if (!message) continue;

    const existingIndex = merged.findIndex(existing => samePersistedMessage(existing, message));
    if (existingIndex >= 0) {
      const existing = merged[existingIndex];
      merged[existingIndex] = {
        ...existing,
        ...message,
        senderId: message.senderId || existing.senderId,
        senderRole: message.senderRole || existing.senderRole,
        senderName: message.senderName || existing.senderName,
        deliveryStatus: 'sent'
      };
    } else {
      merged.push(message);
    }
  }

  return merged.sort((left, right) => {
    const timeDifference = new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime();
    if (timeDifference !== 0) return timeDifference;
    return String(left.id).localeCompare(String(right.id));
  });
}

