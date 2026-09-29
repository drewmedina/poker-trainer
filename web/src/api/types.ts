// Mirrors server/src/main/java/com/pokertrainer/server/api/Dto.java. Keep the two in sync.

export type TableFormat = 'SIX_MAX' | 'HEADS_UP';
export type Verdict = 'GOOD' | 'INACCURACY' | 'MISTAKE';
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
  aiCoach: boolean;
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
  decisions: number;
  goodDecisions: number;
  mistakes: number;
  aiCoach: boolean;
  hand: HandView | null;
}

/** Returned right after an action. The coach's feedback is fetched separately. */
export interface DecisionView {
  handNumber: number;
  index: number;
  street: string;
  actionLabel: string;
}

export interface ActionResult {
  session: SessionView;
  decision: DecisionView;
}

/** source: "ai" (Claude) or "rules" (built-in fallback). */
export interface CoachView {
  verdict: Verdict;
  headline: string;
  explanation: string;
  tip: string;
  source: 'ai' | 'rules';
}

/** Background numbers, only shown in the hand review. */
export interface NumbersView {
  equity: number;
  potOdds: number;
  spr: number;
  chartPosition: string | null;
  chartOpens: boolean | null;
  chartShare: number;
  mathVerdict: Verdict | null;
  mathEvLossBb: number;
  mathNote: string | null;
}

export interface DecisionReview {
  index: number;
  street: string;
  position: string;
  board: string[];
  potBb: number;
  toCallBb: number;
  playersInHand: number;
  actionLabel: string;
  options: string[];
  coach: CoachView;
  numbers: NumbersView;
}

export interface ReviewView {
  handNumber: number;
  finished: boolean;
  heroCards: string[];
  board: string[];
  result: ResultView | null;
  decisions: DecisionReview[];
}
