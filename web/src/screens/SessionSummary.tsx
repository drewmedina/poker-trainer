import type { SessionView } from '../api/types';
import { signedBb } from '../lib/format';

interface Props {
  session: SessionView;
  onPlayAgain: () => void;
  onLobby: () => void;
}

/**
 * First cut of the session summary: the real totals the server tracks today. The street
 * breakdown and "biggest leaks" list from the design need stored decision history (milestone 1).
 */
export function SessionSummary({ session, onPlayAgain, onLobby }: Props) {
  const graded = session.decisions;
  const goodPct = graded === 0 ? 0 : Math.round((session.goodDecisions / graded) * 100);
  return (
    <main className="summary">
      <span className="eyebrow">
        Session · {session.format === 'SIX_MAX' ? '6-max' : 'Heads-up'} · {session.handNumber} hands
      </span>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 16 }}>
        <span className="display big">{signedBb(session.netBb)}</span>
        <span className="eyebrow" style={{ fontSize: 16, paddingBottom: 8 }}>bb {session.netBb >= 0 ? 'won' : 'lost'}</span>
      </div>
      <div className="stat-tiles">
        <Tile label="Decisions" value={String(session.decisions)} />
        <Tile label="Good decisions" value={String(goodPct)} unit="%" />
        <Tile label="Mistakes" value={String(session.mistakes)} />
      </div>
      <p className="muted" style={{ maxWidth: 560, fontSize: 15, lineHeight: 1.5, margin: 0 }}>
        Grades come from the coach. The street breakdown and biggest leaks need stored hand history, which is next on the plan.
      </p>
      <div style={{ display: 'flex', gap: 8, marginTop: 'auto' }}>
        <button type="button" className="btn btn-soft" onClick={onLobby}>Back to lobby</button>
        <button type="button" className="btn btn-cream" onClick={onPlayAgain}>Keep playing</button>
      </div>
    </main>
  );
}

function Tile({ label, value, unit }: { label: string; value: string; unit?: string }) {
  return (
    <div className="stat-tile">
      <span className="muted" style={{ fontSize: 13 }}>{label}</span>
      <span className="display value">
        {value}
        {unit && <span style={{ fontSize: 20, marginLeft: 4 }}>{unit}</span>}
      </span>
    </div>
  );
}
