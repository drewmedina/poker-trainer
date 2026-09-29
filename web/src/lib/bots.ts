// Avatar colors per bot, from the design. Unknown ids fall back to a neutral cream.
const AVATARS: Record<string, { bg: string; fg: string }> = {
  vera: { bg: '#9CC0F2', fg: '#11263F' },
  otis: { bg: '#A6DDB0', fg: '#123A1C' },
  nell: { bg: '#D8BDF0', fg: '#2E1742' },
  rex: { bg: '#E9A77E', fg: '#3A1D0F' },
  sol: { bg: '#F2D27A', fg: '#3D2E07' },
};

export function avatarColors(botId: string | null | undefined) {
  return (botId && AVATARS[botId]) || { bg: '#F1EBDD', fg: '#0F3B2C' };
}
