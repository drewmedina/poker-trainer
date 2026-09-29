const SUITS: Record<string, string> = { c: '♣', d: '♦', h: '♥', s: '♠' };
const RED = new Set(['d', 'h']);

const SIZES = {
  sm: { w: 44, h: 62, rank: 21, small: 9, big: 18, pad: 5, radius: 8 },
  md: { w: 96, h: 134, rank: 42, small: 17, big: 42, pad: 10, radius: 15 },
  lg: { w: 118, h: 166, rank: 56, small: 21, big: 54, pad: 13, radius: 17 },
} as const;

export type CardSize = keyof typeof SIZES;

interface Props {
  code: string; // "Ah", "Td"
  size?: CardSize;
  white?: boolean;
}

/** A face-up card in the Felt v2 style: rank and small suit in the corner, big suit opposite. */
export function PlayingCard({ code, size = 'md', white = false }: Props) {
  const s = SIZES[size];
  const rank = code[0] === 'T' ? '10' : code[0];
  const suit = code[1];
  const color = RED.has(suit) ? 'var(--suit-red)' : 'var(--ink)';
  return (
    <div
      className={`card${white ? ' white' : ''}`}
      style={{ width: s.w, height: s.h, borderRadius: s.radius, color }}
      role="img"
      aria-label={code}
    >
      <div className="corner" style={{ top: s.pad - 2, left: s.pad }}>
        <span className="rank" style={{ fontSize: s.rank }}>{rank}</span>
        <span style={{ fontSize: s.small, lineHeight: 1 }}>{SUITS[suit]}</span>
      </div>
      <div className="big-suit" style={{ right: s.pad, bottom: s.pad - 3, fontSize: s.big }}>
        {SUITS[suit]}
      </div>
    </div>
  );
}

export function CardBack({ width = 26, height = 38, tilt = 0 }: { width?: number; height?: number; tilt?: number }) {
  return <div className="card-back" style={{ width, height, transform: `rotate(${tilt}deg)` }} aria-hidden="true" />;
}

export function CardSlot({ size = 'md' }: { size?: CardSize }) {
  const s = SIZES[size];
  return <div className="card-slot" style={{ width: s.w, height: s.h, borderRadius: s.radius }} aria-hidden="true" />;
}
