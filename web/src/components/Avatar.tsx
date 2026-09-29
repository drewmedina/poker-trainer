import { avatarColors } from '../lib/bots';

export function Avatar({ botId, name, size = 52 }: { botId: string | null; name: string; size?: number }) {
  const { bg, fg } = avatarColors(botId);
  const label = botId ? name[0] : 'YOU';
  return (
    <div
      className="avatar"
      style={{ width: size, height: size, background: bg, color: fg, fontSize: botId ? size / 2 : size / 2.6 }}
      aria-hidden="true"
    >
      {label}
    </div>
  );
}
