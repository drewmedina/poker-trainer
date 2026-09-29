/** 2.7, 100, 0.5 — one decimal only when needed. */
export function bb(value: number): string {
  const rounded = Math.round(value * 10) / 10;
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1);
}

/** Signed with a real minus sign: +18.9, −6.3, 0. */
export function signedBb(value: number): string {
  if (Math.abs(value) < 0.05) return '0';
  return (value > 0 ? '+' : '−') + bb(Math.abs(value));
}

export function pct(value: number): string {
  return `${Math.round(value * 100)}%`;
}
