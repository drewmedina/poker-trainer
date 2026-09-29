import { useCallback, useState } from 'react';
import { api } from './api/client';
import type { SessionView } from './api/types';
import type { DecisionEntry } from './components/CoachSheet';
import { HandReview } from './screens/HandReview';
import { Lobby, type TableSettings } from './screens/Lobby';
import { SessionSummary } from './screens/SessionSummary';
import { Table } from './screens/Table';

type Screen = { name: 'lobby' } | { name: 'table' } | { name: 'review'; hand: number } | { name: 'summary' };

const DEFAULT_SETTINGS: TableSettings = {
  format: 'SIX_MAX',
  preset: 'mixed',
  headsUpBot: 'rex',
  stackBb: 100,
  timing: 'each',
};

export function App() {
  const [screen, setScreen] = useState<Screen>({ name: 'lobby' });
  const [session, setSession] = useState<SessionView | null>(null);
  const [settings, setSettings] = useState<TableSettings>(DEFAULT_SETTINGS);
  // Decisions in the current hand, kept here so they survive a trip to the review screen.
  const [entries, setEntries] = useState<DecisionEntry[]>([]);
  const updateEntries = useCallback((f: (l: DecisionEntry[]) => DecisionEntry[]) => setEntries(f), []);

  async function nextHandFromReview() {
    if (!session) return;
    const s = await api.nextHand(session.id);
    setEntries([]);
    setSession(s);
    setScreen({ name: 'table' });
  }

  const plainHeader = (
    <header className="header">
      <span className="wordmark">TRAINER</span>
    </header>
  );

  return (
    <div className="app">
      {screen.name === 'lobby' && (
        <>
          {plainHeader}
          <Lobby
            initial={settings}
            onStart={(s, chosen) => {
              setSettings(chosen);
              setSession(s);
              setEntries([]);
              setScreen({ name: 'table' });
            }}
          />
        </>
      )}

      {screen.name === 'table' && session?.hand && (
        <Table
          session={session}
          timing={settings.timing}
          entries={entries}
          onEntries={updateEntries}
          onSession={setSession}
          onReview={(hand) => setScreen({ name: 'review', hand })}
          onEnd={() => setScreen({ name: 'summary' })}
        />
      )}

      {screen.name === 'review' && session && (
        <HandReview
          sessionId={session.id}
          handNumber={screen.hand}
          onBack={() => setScreen({ name: 'table' })}
          onNextHand={nextHandFromReview}
        />
      )}

      {screen.name === 'summary' && session && (
        <>
          {plainHeader}
          <SessionSummary
            session={session}
            onPlayAgain={() => setScreen({ name: 'table' })}
            onLobby={() => setScreen({ name: 'lobby' })}
          />
        </>
      )}
    </div>
  );
}
