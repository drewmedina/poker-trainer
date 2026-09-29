import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { BotView, LobbyView, SessionView, TableFormat } from '../api/types';
import { Avatar } from '../components/Avatar';
import { Segmented } from '../components/Segmented';

export type FeedbackTiming = 'each' | 'end';

export interface TableSettings {
  format: TableFormat;
  preset: string;
  headsUpBot: string;
  stackBb: number;
  timing: FeedbackTiming;
}

const PRESET_NOTES: Record<string, string> = {
  mixed: 'One of each style. A good default.',
  soft: 'Leaky players. Learn to exploit.',
  tough: 'Close to solver play. Tests fundamentals.',
};

interface Props {
  initial: TableSettings;
  onStart: (session: SessionView, settings: TableSettings) => void;
}

export function Lobby({ initial, onStart }: Props) {
  const [lobby, setLobby] = useState<LobbyView | null>(null);
  const [settings, setSettings] = useState<TableSettings>(initial);
  const [error, setError] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    api.lobby().then(setLobby).catch((e: Error) => setError(e.message));
  }, []);

  const set = <K extends keyof TableSettings>(key: K, value: TableSettings[K]) =>
    setSettings((s) => ({ ...s, [key]: value }));

  const botsById = new Map((lobby?.bots ?? []).map((b) => [b.id, b]));
  const lineup: BotView[] = (lobby?.presets[settings.preset] ?? [])
    .map((id) => botsById.get(id))
    .filter((b): b is BotView => !!b);
  const sixMax = settings.format === 'SIX_MAX';
  const opponentName = botsById.get(settings.headsUpBot)?.name ?? 'bot';

  async function start() {
    setStarting(true);
    setError(null);
    try {
      const session = await api.createSession({
        format: settings.format,
        preset: sixMax ? settings.preset : undefined,
        lineup: sixMax ? undefined : [settings.headsUpBot],
        stackBb: settings.stackBb,
      });
      onStart(session, settings);
    } catch (e) {
      setError((e as Error).message);
      setStarting(false);
    }
  }

  return (
    <main className="lobby">
      <div className="lobby-top">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          <Segmented
            large
            label="Table format"
            value={settings.format}
            onChange={(v) => set('format', v)}
            options={[
              { value: 'SIX_MAX', label: '6-max table' },
              { value: 'HEADS_UP', label: 'Heads-up' },
            ]}
          />
          <h1 className="display">{sixMax ? 'Take a seat' : 'Pick your opponent'}</h1>
        </div>
        <p className="lobby-intro">
          {sixMax
            ? 'Five bots, each with their own leaks. Play full hands from every position and get feedback on each decision you make.'
            : 'One bot, one leak profile. You see the balanced play and the best play against that bot after each decision.'}
        </p>
      </div>

      {error && <div className="error-banner" style={{ margin: 0 }}>{error}</div>}

      {sixMax ? (
        <>
          <div className="lineup-row">
            <div className="setting">
              Lineup
              <Segmented
                label="Lineup"
                value={settings.preset}
                onChange={(v) => set('preset', v)}
                options={Object.keys(lobby?.presets ?? PRESET_NOTES).map((k) => ({
                  value: k,
                  label: k[0].toUpperCase() + k.slice(1),
                }))}
              />
            </div>
            <span className="muted" style={{ fontSize: 14 }}>{PRESET_NOTES[settings.preset]}</span>
          </div>
          <div className="seat-grid">
            <div className="bot-card you">
              <Avatar botId={null} name="You" />
              <div className="display name">You</div>
              <div className="desc">Your seat moves around the table each hand, so you play every position.</div>
            </div>
            {lineup.map((b, i) => (
              <div className="bot-card" key={`${b.id}-${i}`}>
                <Avatar botId={b.id} name={b.name} />
                <div className="display name">{b.name}</div>
                <span className="tag">{b.style}</span>
                <div className="desc">{b.description}</div>
              </div>
            ))}
          </div>
        </>
      ) : (
        <div className="seat-grid hu">
          {(lobby?.bots ?? []).map((b) => (
            <button
              key={b.id}
              type="button"
              className={`bot-card${settings.headsUpBot === b.id ? ' selected' : ''}`}
              aria-pressed={settings.headsUpBot === b.id}
              onClick={() => set('headsUpBot', b.id)}
            >
              <Avatar botId={b.id} name={b.name} />
              <div className="display name">{b.name}</div>
              <span className="tag">{b.style}</span>
              <div className="desc">{b.description}</div>
            </button>
          ))}
        </div>
      )}

      <div className="settings-bar">
        <div className="setting">
          Stacks
          <Segmented
            mono
            label="Starting stacks"
            value={settings.stackBb}
            onChange={(v) => set('stackBb', v)}
            options={[50, 100, 200].map((v) => ({ value: v, label: `${v}bb` }))}
          />
        </div>
        <div className="setting">
          Feedback
          <Segmented
            label="When to show feedback"
            value={settings.timing}
            onChange={(v) => set('timing', v)}
            options={[
              { value: 'each', label: 'Every decision' },
              { value: 'end', label: 'End of hand' },
            ]}
          />
        </div>
        {lobby && !lobby.aiCoach && (
          <span className="setting" title="Set ANTHROPIC_API_KEY on the server to turn on the AI coach">
            Rules coach (AI coach off)
          </span>
        )}
        <button type="button" className="btn btn-cream sit-down" onClick={start} disabled={starting || !lobby}>
          {starting ? 'DEALING…' : sixMax ? 'SIT DOWN' : `SIT DOWN VS ${opponentName.toUpperCase()}`}
        </button>
      </div>
    </main>
  );
}
