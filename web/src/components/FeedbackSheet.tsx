import { useState } from 'react';
import type { FeedbackView, HandView, Verdict } from '../api/types';
import { bb, pct, signedBb } from '../lib/format';

const VERDICT_STYLE: Record<Verdict, { bg: string; glyph: string; word: string }> = {
  GOOD: { bg: 'var(--good)', glyph: '✓', word: 'Good' },
  INACCURACY: { bg: 'var(--inaccuracy)', glyph: '!', word: 'Inaccuracy' },
  MISTAKE: { bg: 'var(--mistake)', glyph: '✕', word: 'Mistake' },
  INFO: { bg: 'var(--ink-muted)', glyph: 'i', word: 'Info' },
};

interface Props {
  feedback: FeedbackView | null;
  hand: HandView;
  handFeedback: FeedbackView[];
  onContinue: () => void;
  onNextHand: () => void;
  busy: boolean;
}

/**
 * The cream bottom sheet. Shows feedback on the decision just made and, when the hand is over,
 * the result. With "end of hand" timing it opens only at the end and lists every decision.
 */
export function FeedbackSheet({ feedback, hand, handFeedback, onContinue, onNextHand, busy }: Props) {
  const over = hand.result !== null;
  const [collapsed, setCollapsed] = useState(false);
  const primary = over ? (
    <button type="button" className="btn btn-ink" onClick={onNextHand} disabled={busy} autoFocus>
      Next hand
    </button>
  ) : (
    <button type="button" className="btn btn-ink" onClick={onContinue} autoFocus>
      Continue
    </button>
  );

  return (
    <section className={`sheet${collapsed ? ' collapsed' : ''}`} aria-live="polite" aria-label="Feedback">
      <button
        type="button"
        className="sheet-handle"
        aria-label={collapsed ? 'Show feedback details' : 'Hide feedback details to see the table'}
        aria-expanded={!collapsed}
        onClick={() => setCollapsed((c) => !c)}
      />
      {feedback ? (
        <>
          <div className="sheet-head">
            <div className="verdict-disc" style={{ background: VERDICT_STYLE[feedback.verdict].bg }} aria-hidden="true">
              {VERDICT_STYLE[feedback.verdict].glyph}
            </div>
            <div className={`display${feedback.verdict === 'INFO' ? ' small' : ''}`}>{feedback.headline}</div>
            {feedback.verdict !== 'INFO' && (
              <span className="chip-ink mono">EV −{bb(feedback.evLossBb)}bb</span>
            )}
            {feedback.estimate && (
              <span className="chip-dashed">{feedback.multiway ? 'Multiway · estimate' : 'Estimate'}</span>
            )}
            <div className="sheet-actions">{primary}</div>
          </div>
          <p>{feedback.detail}</p>
          <div className="stats">
            <span>You: <strong>{feedback.actionLabel}</strong></span>
            <span>Equity {pct(feedback.equity)}</span>
            {feedback.potOdds > 0 && <span>Needed {pct(feedback.potOdds)}</span>}
          </div>
          <Frequencies freqs={feedback.frequencies} picked={feedback.actionLabel} />
        </>
      ) : (
        <div className="sheet-head">
          <div className="display">Hand over</div>
          <div className="sheet-actions">{primary}</div>
        </div>
      )}

      {over && hand.result && (
        <div className="result-row">
          <div className="display" style={{ color: hand.result.heroNetBb < 0 ? 'var(--mistake)' : 'var(--ink)' }}>
            {signedBb(hand.result.heroNetBb)}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <span style={{ fontSize: 14, fontWeight: 600 }}>{hand.result.summary}</span>
            {handFeedback.length > 0 && (
              <div className="decision-list">
                {handFeedback.map((f, i) => (
                  <span className="decision-chip" key={i}>
                    <span className="dot" style={{ background: VERDICT_STYLE[f.verdict].bg }}>
                      {VERDICT_STYLE[f.verdict].glyph}
                    </span>
                    {f.actionLabel}
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
}

function Frequencies({ freqs, picked }: { freqs: Record<string, number>; picked: string }) {
  const entries = Object.entries(freqs);
  if (entries.length === 0) return null;
  return (
    <div className="freq">
      <div className="bar">
        {entries.map(([label, f]) => (
          <div key={label} className={picked.startsWith(label) ? 'picked' : undefined} style={{ width: `${f * 100}%` }} />
        ))}
      </div>
      <div style={{ display: 'flex', fontSize: 13 }}>
        {entries.map(([label, f]) => (
          <div key={label} style={{ width: `${f * 100}%` }}>
            {label} {pct(f)}
          </div>
        ))}
      </div>
    </div>
  );
}
