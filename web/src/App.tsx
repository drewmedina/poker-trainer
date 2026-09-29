import { useState } from 'react';
import type { SessionView } from './api/types';
import { Lobby, type TableSettings } from './screens/Lobby';
import { SessionSummary } from './screens/SessionSummary';
import { Table } from './screens/Table';

type Screen = 'lobby' | 'table' | 'summary';

const DEFAULT_SETTINGS: TableSettings = {
  format: 'SIX_MAX',
  preset: 'mixed',
  headsUpBot: 'rex',
  stackBb: 100,
  timing: 'each',
};

export function App() {
  const [screen, setScreen] = useState<Screen>('lobby');
  const [session, setSession] = useState<SessionView | null>(null);
  const [settings, setSettings] = useState<TableSettings>(DEFAULT_SETTINGS);

  return (
    <div className="app">
      {screen === 'lobby' && (
        <>
          <header className="header">
            <span className="wordmark">TRAINER</span>
          </header>
          <Lobby
            initial={settings}
            onStart={(s, chosen) => {
              setSettings(chosen);
              setSession(s);
              setScreen('table');
            }}
          />
        </>
      )}

      {screen === 'table' && session?.hand && (
        <Table session={session} timing={settings.timing} onSession={setSession} onEnd={() => setScreen('summary')} />
      )}

      {screen === 'summary' && session && (
        <>
          <header className="header">
            <span className="wordmark">TRAINER</span>
          </header>
          <SessionSummary session={session} onPlayAgain={() => setScreen('table')} onLobby={() => setScreen('lobby')} />
        </>
      )}
    </div>
  );
}
