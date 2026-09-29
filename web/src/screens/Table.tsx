import { useCallback, useEffect, useState, type CSSProperties } from 'react';
import { api } from '../api/client';
import type { ActionView, FeedbackView, SeatView, SessionView } from '../api/types';
import { Avatar } from '../components/Avatar';
import { FeedbackSheet } from '../components/FeedbackSheet';
import { CardBack, CardSlot, PlayingCard } from '../components/PlayingCard';
import { bb, signedBb } from '../lib/format';
import type { FeedbackTiming } from './Lobby';

// Where each opponent sits, as % of the table area, by table size and seats clockwise from you.
const LAYOUTS: Record<number, { x: number; y: number }[]> = {
  2: [{ x: 50, y: 3 }],
  3: [{ x: 16, y: 20 }, { x: 84, y: 20 }],
  4: [{ x: 16, y: 40 }, { x: 50, y: 3 }, { x: 84, y: 40 }],
  5: [{ x: 16, y: 58 }, { x: 28, y: 5 }, { x: 72, y: 5 }, { x: 84, y: 58 }],
  6: [{ x: 16, y: 60 }, { x: 16, y: 14 }, { x: 50, y: 3 }, { x: 84, y: 14 }, { x: 84, y: 60 }],
};

const STREETS = ['PREFLOP', 'FLOP', 'TURN', 'RIVER'] as const;

interface Props {
  session: SessionView;
  timing: FeedbackTiming;
  onSession: (s: SessionView) => void;
  onEnd: () => void;
}

export function Table({ session, timing, onSession, onEnd }: Props) {
  const hand = session.hand!;
  const [feedback, setFeedback] = useState<FeedbackView | null>(null);
  const [handFeedback, setHandFeedback] = useState<FeedbackView[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const heroToAct = hand.result === null && hand.toAct === hand.heroSeat;
  const sheetOpen = feedback !== null || hand.result !== null;

  const act = useCallback(
    async (action: ActionView) => {
      if (busy) return;
      setBusy(true);
      setError(null);
      try {
        const r = await api.act(session.id, action);
        onSession(r.session);
        setHandFeedback((list) => [...list, r.feedback]);
        if (timing === 'each') setFeedback(r.feedback);
      } catch (e) {
        setError((e as Error).message);
      } finally {
        setBusy(false);
      }
    },
    [busy, session.id, onSession, timing],
  );

  const nextHand = useCallback(async () => {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const s = await api.nextHand(session.id);
      setFeedback(null);
      setHandFeedback([]);
      onSession(s);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }, [busy, session.id, onSession]);

  // Keyboard: 1-9 pick an action, Space or Enter continues.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.target instanceof HTMLInputElement) return;
      if (sheetOpen && (e.key === ' ' || e.key === 'Enter')) {
        e.preventDefault();
        if (hand.result) nextHand();
        else setFeedback(null);
        return;
      }
      const n = Number(e.key);
      if (!sheetOpen && heroToAct && n >= 1 && n <= hand.legalActions.length) {
        act(hand.legalActions[n - 1]);
      }
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [sheetOpen, heroToAct, hand, act, nextHand]);

  const hero = hand.seats[hand.heroSeat];
  const others = hand.seats.filter((s) => !s.hero);
  const layout = LAYOUTS[hand.seats.length];
  const inHand = hand.seats.filter((s) => !s.folded).length;
  const streetIndex = STREETS.indexOf(hand.street);

  return (
    <>
      <header className="header">
        <button type="button" className="wordmark" onClick={onEnd}>TRAINER</button>
        <div className="pill street-indicator" aria-label={`Street: ${hand.street}`}>
          {STREETS.map((st, i) => (
            <span key={st} className={`dot${i < streetIndex || hand.result ? ' done' : ''}${i === streetIndex && !hand.result ? ' now' : ''}`} />
          ))}
          <span className="label">
            {hand.result ? 'HAND OVER' : `${hand.street}${hand.seats.length > 2 ? ` · ${inHand} PLAYERS` : ''}`}
          </span>
        </div>
        <div className="header-right">
          <span className="pill"><span className="muted">Hand</span><span className="mono">{session.handNumber}</span></span>
          <span className="pill"><span className="muted">Net</span><span className="mono">{signedBb(session.netBb)}bb</span></span>
          <span className="pill"><span className="muted">EV lost</span><span className="mono">{bb(session.evLostBb)}bb</span></span>
          <button type="button" className="pill" onClick={onEnd} style={{ fontWeight: 600 }}>End session</button>
        </div>
      </header>

      {error && <div className="error-banner">{error}</div>}

      <main className="table">
        {others.map((seat) => {
          const offset = (seat.seat - hand.heroSeat + hand.seats.length) % hand.seats.length;
          const pos = layout[offset - 1];
          return <Seat key={seat.seat} seat={seat} style={{ left: `${pos.x}%`, top: `${pos.y}%` }} />;
        })}

        <div className="center">
          <div className="pot">
            <span className="eyebrow" style={{ paddingBottom: 4 }}>Pot</span>
            <span className="display value">{bb(hand.potBb)}</span>
          </div>
          <div className="board">
            {[0, 1, 2, 3, 4].map((i) =>
              hand.board[i] ? <PlayingCard key={i} code={hand.board[i]} /> : <CardSlot key={i} />,
            )}
          </div>
        </div>

        <div className="hero" style={{ opacity: hero.folded ? 0.45 : 1 }}>
          <div className="hero-cards">
            {hero.cards?.map((c) => <PlayingCard key={c} code={c} size="lg" white />)}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
              <span className="display" style={{ fontSize: 44 }}>You</span>
              <span className={`pos-chip${hero.button ? ' btn-pos' : ''}`}>{hero.position}</span>
            </div>
            <span className="mono muted" style={{ fontSize: 13 }}>{bb(hero.stackBb)}bb</span>
            {hero.handName && <span style={{ fontSize: 14 }}>{hero.handName}</span>}
            {!hero.handName && hero.lastAction && <span className="status">{hero.lastAction}</span>}
          </div>
        </div>

        {!sheetOpen && heroToAct && (
          <nav className="dock" aria-label="Your action">
            {hand.legalActions.map((a, i) => (
              <button key={`${a.type}-${a.amount}`} type="button" onClick={() => act(a)} disabled={busy}>
                <span className="label">{a.type === 'CALL' ? 'Call' : a.label}</span>
                {a.type !== 'FOLD' && a.type !== 'CHECK' && <span className="amount">{bb(a.amountBb)}</span>}
                {i < 9 && <kbd>{i + 1}</kbd>}
              </button>
            ))}
          </nav>
        )}
        {!sheetOpen && !heroToAct && <div className="waiting">Dealing…</div>}

        {sheetOpen && (
          <FeedbackSheet
            feedback={feedback}
            hand={hand}
            handFeedback={handFeedback}
            onContinue={() => setFeedback(null)}
            onNextHand={nextHand}
            busy={busy}
          />
        )}
      </main>
    </>
  );
}

function Seat({ seat, style }: { seat: SeatView; style: CSSProperties }) {
  const revealed = seat.cards !== null;
  return (
    <div className={`seat${seat.folded ? ' folded' : ''}`} style={style}>
      <div className="seat-row">
        <Avatar botId={seat.botId} name={seat.name} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
            <span className="display seat-name">{seat.name}</span>
            <span className={`pos-chip${seat.button ? ' btn-pos' : ''}`}>{seat.position}</span>
          </div>
          <span className="mono muted" style={{ fontSize: 13 }}>{bb(seat.stackBb)}bb</span>
        </div>
        {!seat.folded && !revealed && (
          <div style={{ display: 'flex', marginLeft: 4 }}>
            <CardBack tilt={-6} />
            <div style={{ marginLeft: -9 }}><CardBack tilt={6} /></div>
          </div>
        )}
      </div>
      {revealed && (
        <div style={{ display: 'flex', gap: 4 }}>
          {seat.cards!.map((c) => <PlayingCard key={c} code={c} size="sm" />)}
        </div>
      )}
      {seat.handName ? (
        <span className="status">{seat.handName}</span>
      ) : seat.lastAction ? (
        <span className={`status${seat.folded ? ' quiet' : ''}`}>{seat.lastAction}</span>
      ) : null}
    </div>
  );
}
