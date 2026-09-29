import type {
  ActionResult,
  ActionView,
  CoachView,
  CreateSessionRequest,
  LobbyView,
  ReviewView,
  SessionView,
} from './types';

export class ApiError extends Error {}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, {
      headers: { 'Content-Type': 'application/json' },
      ...init,
    });
  } catch {
    throw new ApiError('Cannot reach the server. Is it running on port 7070?');
  }
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`;
    try {
      const body = await res.json();
      if (body?.error) message = body.error;
    } catch {
      /* not JSON */
    }
    throw new ApiError(message);
  }
  return res.json() as Promise<T>;
}

export const api = {
  lobby: () => request<LobbyView>('/api/lobby'),

  createSession: (body: CreateSessionRequest) =>
    request<SessionView>('/api/sessions', { method: 'POST', body: JSON.stringify(body) }),

  nextHand: (sessionId: string) =>
    request<SessionView>(`/api/sessions/${sessionId}/hands`, { method: 'POST' }),

  act: (sessionId: string, action: ActionView) =>
    request<ActionResult>(`/api/sessions/${sessionId}/actions`, {
      method: 'POST',
      body: JSON.stringify({ type: action.type, amount: action.amount }),
    }),

  /** Waits on the server until the coach has answered (a few seconds at most). */
  coach: (sessionId: string, hand: number, index: number) =>
    request<CoachView>(`/api/sessions/${sessionId}/hands/${hand}/decisions/${index}/coach`),

  review: (sessionId: string, hand: number) =>
    request<ReviewView>(`/api/sessions/${sessionId}/hands/${hand}/review`),
};
