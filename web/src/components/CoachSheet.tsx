import { useState } from 'react';
import type { CoachView, DecisionView, HandView, Verdict } from '../api/types';
import { signedBb } from '../lib/format';

export const VERDICT_STYLE: Record<Verdict, { bg: string; glyph: string; word: string }> = {
  GOOD: { bg: 'var(--good)', glyph: '✓', word: 'Good' },
  INACCURACY: { bg: 'var(--inaccuracy)', glyph: '!', word: 'Inaccuracy' },
  MISTAKE: { bg: 'var(--mistake)', glyph: '✕', word: 'Mistake' },
};

/** One hero decision this hand, and the coach's answer once it arrives. */
export interface DecisionEntry {
  decision: DecisionView;
  coach: CoachView | null;
  error?: string;
}

interface Props {
  entry: DecisionEntry | null; // the decision to show; null shows only the hand result
  hand: HandView;
  entries: DecisionEntry[];
  onContinue: () => void;
  onNextHand: () => void;
  onReview: () => void;
  busy: boolean;
}

/**
 * The cream bottom sheet. After each decision it shows the coach's verdict and a short
 * explanation, and nothing else: numbers live in the hand review. When the hand is over it
 * adds the result and a way into the review.
 */
export function CoachSheet({ entry, hand, entries, onContinue, onNextHand, onReview, busy }: Props) {
  const over = hand.result !== null;
  const coach = entry?.coach ?? null;
  const loading = entry !== null && coach === null && !entry.error;
  const [collapsed, setCollapsed] = useState(false);

  const buttons = over ? (
    <>
      {entries.length > 0 && (
        <button type="button" className="btn btn-ghost-ink" onClick={onReview}>
          Review hand
        </button>
      )}
      <button type="button" className="btn btn-ink" onClick={onNextHand} disabled={busy} autoFocus>
        Next hand
      </button>
    </>
  ) : (
    <button type="button" className="btn btn-ink" onClick={onContinue} autoFocus>
      Continue
    </button>
  );

  return (
    <section className={`sheet${collapsed ? ' collapsed' : ''}`} aria-live="polite" aria-busy={loading} aria-label="Coach feedback">
      <button
        type="button"
        className="sheet-handle"
        aria-label={collapsed ? 'Show feedback details' : 'Hide feedback details to see the table'}
        aria-expanded={!collapsed}
        onClick={() => setCollapsed((c) => !c)}
      />
      {entry ? (
        <>
          <div className="sheet-head">
            {coach ? (
              <div className="verdict-disc" style={{ background: VERDICT_STYLE[coach.verdict].bg }} aria-hidden="true">
                {VERDICT_STYLE[coach.verdict].glyph}
              </div>
            ) : (
              <div className="verdict-disc thinking" aria-hidden="true" />
            )}
            <div className="display">
              {coach ? coach.headline : loading ? 'Coach is thinking' : 'No feedback'}
            </div>
            <span className="chip-ink">You: {entry.decision.actionLabel}</span>
            <div className="sheet-actions">{buttons}</div>
          </div>
          {loading && (
            <div className="skeleton" aria-hidden="true">
              <span style={{ width: '82%' }} />
              <span style={{ width: '56%' }} />
            </div>
          )}
          {entry.error && <p>{entry.error}</p>}
          {coach && (
            <>
              <p>{coach.explanation}</p>
              <div className="tip">
                <span className="tip-label">Tip</span>
                <span>{coach.tip}</span>
              </div>
            </>
          )}
        </>
      ) : (
        <div className="sheet-head">
          <div className="display">Hand over</div>
          <div className="sheet-actions">{buttons}</div>
        </div>
      )}

      {over && hand.result && (
        <div className="result-row">
          <div className="display" style={{ color: hand.result.heroNetBb < 0 ? 'var(--mistake)' : 'var(--ink)' }}>
            {signedBb(hand.result.heroNetBb)}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <span style={{ fontSize: 14, fontWeight: 600 }}>{hand.result.summary}</span>
            {entries.length > 0 && <DecisionChips entries={entries} />}
          </div>
        </div>
      )}
    </section>
  );
}

export function DecisionChips({ entries }: { entries: DecisionEntry[] }) {
  return (
    <div className="decision-list">
      {entries.map((e) => (
        <span className="decision-chip" key={e.decision.index}>
          <span className="dot" style={{ background: e.coach ? VERDICT_STYLE[e.coach.verdict].bg : 'var(--ink-muted)' }}>
            {e.coach ? VERDICT_STYLE[e.coach.verdict].glyph : '…'}
          </span>
          {e.decision.actionLabel}
        </span>
      ))}
    </div>
  );
}
