// Mirrors server/src/main/java/com/pokertrainer/server/api/Dto.java. Keep the two in sync.

export type TableFormat = 'SIX_MAX' | 'HEADS_UP';
export type Verdict = 'GOOD' | 'INACCURACY' | 'MISTAKE' | 'INFO';
export type ActionType = 'FOLD' | 'CHECK' | 'CALL' | 'BET' | 'RAISE';

export interface BotView {
  id: string;
  name: string;
  style: string;
  description: string;
}

export interface LobbyView {
  bots: BotView[];
  presets: Record<string, string[]>;
}

export interface CreateSessionRequest {
  format: TableFormat;
  preset?: string;
  lineup?: string[];
  stackBb?: number;
  seed?: number;
}

export interface SeatView {
  seat: number;
  name: string;
  botId: string | null;
  style: string | null;
  position: string;
  stackBb: number;
  committedBb: number;
  folded: boolean;
  allIn: boolean;
  button: boolean;
  hero: boolean;
  cards: string[] | null;
  lastAction: string | null;
  handName: string | null;
}

export interface ActionView {
  type: ActionType;
  amount: number; // chips; send back unchanged
  label: string;
  amountBb: number;
  allIn: boolean;
}

export interface LogView {
  seat: number;
  name: string;
  street: string;
  text: string;
}

export interface ResultView {
  showdown: boolean;
  winners: number[];
  heroNetBb: number;
  summary: string;
}

export interface HandView {
  handNumber: number;
  street: 'PREFLOP' | 'FLOP' | 'TURN' | 'RIVER';
  potBb: number;
  potBeforeStreetBb: number;
  board: string[];
  seats: SeatView[];
  heroSeat: number;
  toAct: number;
  legalActions: ActionView[];
  log: LogView[];
  result: ResultView | null;
}

export interface SessionView {
  id: string;
  format: TableFormat;
  handNumber: number;
  netBb: number;
  evLostBb: number;
  decisions: number;
  goodDecisions: number;
  mistakes: number;
  hand: HandView | null;
}

export interface FeedbackView {
  verdict: Verdict;
  evLossBb: number;
  headline: string;
  detail: string;
  equity: number;
  potOdds: number;
  frequencies: Record<string, number>;
  multiway: boolean;
  estimate: boolean;
  source: string;
  actionLabel: string;
}

export interface ActionResult {
  session: SessionView;
  feedback: FeedbackView;
}
