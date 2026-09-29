import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { DecisionReview, ReviewView } from '../api/types';
import { VERDICT_STYLE } from '../components/CoachSheet';
import { PlayingCard } from '../components/PlayingCard';
import { bb, pct, signedBb } from '../lib/format';

interface Props {
  sessionId: string;
  handNumber: number;
  onBack: () => void;
  onNextHand: () => void;
}

const STREET_NAMES: Record<string, string> = { PREFLOP: 'Preflop', FLOP: 'Flop', TURN: 'Turn', RIVER: 'River' };

/**
 * End-of-hand review. Every decision with the coach's verdict, plus the background numbers
 * (equity, price, stack-to-pot, opening chart) that the live sheet deliberately leaves out.
 */
export function HandReview({ sessionId, handNumber, onBack, onNextHand }: Props) {
  const [review, setReview] = useState<ReviewView | null>(null);
  const [selected, setSelected] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .review(sessionId, handNumber)
      .then((r) => {
        setReview(r);
        // Open on the costliest decision: first mistake, else first inaccuracy, else the first one.
        const worst =
          r.decisions.findIndex((d) => d.coach.verdict === 'MISTAKE') >= 0
            ? r.decisions.findIndex((d) => d.coach.verdict === 'MISTAKE')
            : Math.max(0, r.decisions.findIndex((d) => d.coach.verdict === 'INACCURACY'));
        setSelected(worst);
      })
      .catch((e: Error) => setError(e.message));
  }, [sessionId, handNumber]);

  const d = review?.decisions[selected];

  return (
    <>
      <header className="header">
        <button type="button" className="wordmark" onClick={onBack}>TRAINER</button>
        <span className="pill" style={{ fontWeight: 600, letterSpacing: '0.06em' }}>HAND {handNumber} · REVIEW</span>
        <div className="header-right">
          <button type="button" className="btn btn-soft" style={{ height: 44 }} onClick={onBack}>Back to table</button>
          <button type="button" className="btn btn-cream" style={{ height: 44 }} onClick={onNextHand}>Next hand</button>
        </div>
      </header>

      {error && <div className="error-banner">{error}</div>}
      {!review && !error && <div className="review-loading">Loading the review…</div>}

      {review && (
        <main className="review">
          <aside className="review-side">
            {review.result && (
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12 }}>
                <span className="display" style={{ fontSize: 96, color: review.result.heroNetBb < 0 ? '#f28a7c' : undefined }}>
                  {signedBb(review.result.heroNetBb)}
                </span>
                <span className="eyebrow" style={{ paddingBottom: 6 }}>bb · {review.result.summary}</span>
              </div>
            )}
            <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ display: 'flex', gap: 4 }}>
                {review.heroCards.map((c) => <PlayingCard key={c} code={c} size="sm" white />)}
              </div>
              <div style={{ width: 1, height: 52, background: 'var(--line)' }} />
              <div style={{ display: 'flex', gap: 4 }}>
                {review.board.map((c) => <PlayingCard key={c} code={c} size="sm" />)}
                {review.board.length === 0 && <span className="muted" style={{ fontSize: 13 }}>No board: the hand ended preflop</span>}
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <span className="eyebrow" style={{ marginBottom: 4 }}>Your decisions</span>
              {review.decisions.length === 0 && <span className="muted">You did not act this hand.</span>}
              {review.decisions.map((x, i) => (
                <button
                  key={x.index}
                  type="button"
                  className={`review-step${i === selected ? ' selected' : ''}`}
                  aria-pressed={i === selected}
                  onClick={() => setSelected(i)}
                >
                  <span className="dot" style={{ background: VERDICT_STYLE[x.coach.verdict].bg }}>{VERDICT_STYLE[x.coach.verdict].glyph}</span>
                  <span style={{ display: 'flex', flexDirection: 'column', gap: 2, flexGrow: 1 }}>
                    <span style={{ fontSize: 12, opacity: 0.7 }}>{STREET_NAMES[x.street]} · {x.position}</span>
                    <span style={{ fontSize: 15, fontWeight: 700 }}>{x.actionLabel}</span>
                  </span>
                  <span style={{ fontSize: 12, opacity: 0.8 }}>{VERDICT_STYLE[x.coach.verdict].word}</span>
                </button>
              ))}
            </div>
          </aside>

          {d && <DecisionDetail d={d} />}
        </main>
      )}
    </>
  );
}

function DecisionDetail({ d }: { d: DecisionReview }) {
  const n = d.numbers;
  const style = VERDICT_STYLE[d.coach.verdict];
  return (
    <section className="review-main">
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 24 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <span className="eyebrow">
            {STREET_NAMES[d.street]} · {d.position} · pot {bb(d.potBb)}bb · {d.playersInHand} players
            {d.toCallBb > 0 ? ` · ${bb(d.toCallBb)} to call` : ''}
          </span>
          <h1 className="display" style={{ margin: 0, fontSize: 60 }}>You: {d.actionLabel}</h1>
        </div>
        <span className="verdict-pill" style={{ background: style.bg }}>{style.glyph} {style.word}</span>
      </div>

      {d.board.length > 0 && (
        <div style={{ display: 'flex', gap: 6 }}>
          {d.board.map((c) => <PlayingCard key={c} code={c} size="sm" />)}
        </div>
      )}

      <div className="review-grid">
        <div className="coach-card">
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span className="display" style={{ fontSize: 30 }}>Coach</span>
            <span className="chip-ink">{d.coach.headline}</span>
          </div>
          <p>{d.coach.explanation}</p>
          <div className="tip">
            <span className="tip-label">Tip</span>
            <span>{d.coach.tip}</span>
          </div>
          <span style={{ marginTop: 'auto', fontSize: 12, color: 'var(--ink-muted)' }}>
            {d.coach.source === 'ai' ? 'Written by Claude from the table state' : 'Rules coach (no API key set)'}
          </span>
        </div>

        <div className="numbers-card">
          <span style={{ fontSize: 15, fontWeight: 700 }}>The numbers</span>
          <div className="numbers">
            <Stat label="Your equity" value={pct(n.equity)} note={`vs random hands of ${d.playersInHand - 1} player${d.playersInHand > 2 ? 's' : ''}`} />
            {n.potOdds > 0 && <Stat label="Needed to call" value={pct(n.potOdds)} note="pot odds" />}
            <Stat label="Stack to pot" value={n.spr.toFixed(1)} note="effective stack / pot" />
            {n.chartOpens !== null && (
              <Stat
                label={`${n.chartPosition} opening chart`}
                value={n.chartOpens ? 'Open' : 'Fold'}
                note={`opens about ${pct(n.chartShare)} of hands`}
              />
            )}
            {n.mathVerdict && (
              <Stat
                label="Price check"
                value={n.mathEvLossBb > 0.05 ? `−${bb(n.mathEvLossBb)}` : 'OK'}
                note={n.mathEvLossBb > 0.05 ? 'bb given up vs the other option' : 'the choice fits the price'}
              />
            )}
          </div>
          {n.mathNote && <p className="muted" style={{ margin: 0, fontSize: 14, lineHeight: 1.5 }}>{n.mathNote}</p>}
          <div>
            <span className="eyebrow">Your options</span>
            <div className="options">
              {d.options.map((o) => (
                <span key={o} className={`option${o === d.actionLabel ? ' chosen' : ''}`}>{o}</span>
              ))}
            </div>
          </div>
          <p className="muted" style={{ margin: 0, fontSize: 12, lineHeight: 1.5 }}>
            Equity is measured against random hands, so it runs high when opponents have bet or raised. Opening charts are
            approximate. Full solver lines come later.
          </p>
        </div>
      </div>
    </section>
  );
}

function Stat({ label, value, note }: { label: string; value: string; note: string }) {
  return (
    <div className="stat">
      <span className="muted" style={{ fontSize: 12 }}>{label}</span>
      <span className="display" style={{ fontSize: 40 }}>{value}</span>
      <span className="muted" style={{ fontSize: 11 }}>{note}</span>
    </div>
  );
}
