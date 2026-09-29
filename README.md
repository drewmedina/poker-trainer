# Poker Trainer

Play full no-limit hold'em hands against bots, at a 6-max table or heads-up, and get feedback on
every decision you make. Feedback combines solver-style numbers (what a balanced strategy does)
with opponent-aware advice (what wins the most against this particular bot).

This repo is the first working slice: a real game engine, an HTTP API, and the web table from the
Felt v2 designs. Feedback is a placeholder heuristic for now (see "What is real and what is a
placeholder" below).

## Layout

```
engine/   Pure Java, no dependencies. Cards, hand evaluator, equity, ranges, the 2-6 player
          game state machine (positions, min-raises, side pots), placeholder bots, feedback.
server/   Javalin HTTP API. Sessions, table lineups, JSON views. Thin on purpose.
web/      React + TypeScript + Vite. Lobby, table, feedback sheet, session summary.
```

## Running it locally

Requirements: JDK 21, Gradle 8.x (only once, to create the wrapper), Node 20+.

```bash
# one time: create the Gradle wrapper
gradle wrapper

# terminal 1: API on http://localhost:7070
./gradlew :server:run

# terminal 2: web app on http://localhost:5173 (proxies /api to 7070)
cd web
npm install
npm run dev
```

Tests:

```bash
./gradlew test          # engine + server
cd web && npm run build # typecheck + production build
```

## API

| Method | Path | What it does |
| --- | --- | --- |
| GET | `/api/lobby` | Bots and lineup presets |
| POST | `/api/sessions` | `{format: "SIX_MAX" \| "HEADS_UP", preset?, lineup?, stackBb?, seed?}`. Creates a session and deals the first hand |
| GET | `/api/sessions/{id}` | Current session and hand |
| POST | `/api/sessions/{id}/actions` | `{type, amount}` copied from one of `hand.legalActions`. Returns the new state and feedback on that decision |
| POST | `/api/sessions/{id}/hands` | Deals the next hand |

The server runs bots until it is the hero's turn or the hand ends, so every response is a state
where the hero has something to do (or the hand is over).

## Design decisions worth knowing

- **Chips are ints.** 100 chips is one big blind. No floating point in game logic. The API sends
  big blinds as doubles for display, but actions carry chip amounts copied from `legalActions`.
- **Fixed bet sizes.** 33%, 75%, 150% of pot, plus all-in; preflop opens of 2.5bb; raises of 3x or
  pot. This keeps hands on trees a solver can cover, and it is also the clean UI in the designs.
- **Cards are ints 0-51** (`rank * 4 + suit`) and ranges are weights over all 1326 combos, so card
  removal is automatic.
- **Stacks reset each hand** to the chosen depth, so every spot is at a known stack depth.
- **The hero is always seat 0** and the button moves, so you play every position.
- **Numbers never come from the language model.** The coach layer (later) only explains structured
  feedback that the solver or heuristic produced.

## What is real and what is a placeholder

Real and tested: hand evaluation, equity (exact on the turn, Monte Carlo otherwise), range parsing,
the full betting state machine for 2-6 players including side pots and uncalled bets, sessions and
the API. A fuzz test plays thousands of random hands at every table size and checks every chip.

Placeholders, clearly marked in code:

- `ProfileBot` decides from equity against random hands plus a few personality knobs.
- `HeuristicFeedbackProvider` grades only call/fold decisions (equity vs pot odds) and returns an
  ungraded INFO result for bets, checks and raises. Everything it returns is flagged as an estimate.
- Sessions live in memory.

See the development plan for the order these get replaced.
