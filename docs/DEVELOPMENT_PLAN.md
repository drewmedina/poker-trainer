# Poker Trainer: development plan

## Product in one paragraph

Sit at a 6-max table (the default) or play heads-up against bots with distinct leaks. Play full
hands. After each decision, a feedback sheet says how good it was, what a balanced strategy does,
and what wins the most against these particular opponents. Sessions end with a summary of where
you lost EV and which leaks to drill.

## Stack

- Engine and API: Java 21 (engine has no dependencies; API on Javalin + Jackson)
- Web: React + TypeScript + Vite, styled from the Felt v2 designs
- Later: Postgres for hand history, an external solver process for strategies, the Anthropic API
  for coach explanations

## Where things stand (milestone 0, done)

- Engine: evaluator, equity, 1326-combo ranges, 2-6 player betting with side pots. 35 engine tests
  plus a fuzz test over thousands of random hands.
- API: sessions for 6-max and heads-up, lineup presets, legal action menus, hand logs, results.
- Web: lobby (format, lineup, stacks, feedback timing), 6-max and heads-up table, feedback sheet,
  hand result, session summary. Keyboard shortcuts 1-9 and Space.
- Coach: every decision gets feedback from Claude (verdict, headline, explanation, tip) with a
  deterministic rules coach as the fallback. Background numbers (equity, pot odds, stack-to-pot,
  approximate opening charts) feed the coach and show only in the end-of-hand review.
- Placeholders: bots use equity + personality knobs; equity is vs random hands; charts are approximate.

## Milestones

### 1. Make it a real project (about a week)
- Run `gradle wrapper`, push to GitHub, add CI (GitHub Actions: `./gradlew test`, `npm run build`).
- Persist sessions, hands and decisions in Postgres (Flyway migrations). Hands need to be
  replayable for the review screen, so store the deck seed plus the action list.
- Build the hand review screen and the real session summary (EV lost by street, biggest leaks)
  from stored decisions.

### 2. Preflop done properly (1-2 weeks)
- Preflop is most decisions and the easiest to solve. Generate or source 6-max 100bb charts per
  position: open, call vs open, 3-bet, call vs 3-bet, 4-bet. Store as `Range` weights per action.
- Grade every preflop decision against the chart (frequency plus EV loss where available).
- Bots play preflop from the same charts with their profile's deviations. This alone makes the
  6-max table feel much more realistic.

### 3. Postflop solver for heads-up pots (2-4 weeks, the hard part)
- Most 6-max flops are heads-up, so this covers the majority of postflop decisions.
- Run an open-source solver (TexasSolver is the usual choice; check its license before bundling)
  as a separate process behind a `SolverService` interface.
- Precompute a flop library for common single-raised pots with the fixed bet-size menu. Solve
  turn and river subgames live, since both ranges are known by then. Cache results by spot.
- Add `SolverFeedbackProvider` in front of the heuristic: exact frequencies and EV loss when a
  solution exists, heuristic fallback otherwise.

### 4. Opponent-aware feedback (1-2 weeks)
- Turn each bot profile into node-locked adjustments of the solver strategy (Rex over-bluffs
  rivers, Otis under-folds, and so on). Bots play the locked strategy.
- Compute the best response against the locked strategy. That powers the "Against Rex" column:
  the EV of each action against this bot, next to the balanced frequencies.

### 5. Coach follow-ups (a few days)
- The per-decision coach is done. As solver data lands (milestones 2-4), pass it into the coach
  prompt so verdicts get sharper, and show solver lines in the hand review.
- Add "Ask a follow-up" on the review screen with the same structured context.

### 6. Multiway pots
- Keep feedback in 3+ way pots as clearly labeled estimates: equity against each opponent's
  estimated range (from their actions), pot odds, and simple rules of thumb.
- Revisit multiway solving later; it is still an open research area and expensive.

### Later
- Phone layout (designs exist for the table and feedback sheet), drills for specific leaks,
  tournament stack depths and ICM.

## Risks to watch

- Solver cost and latency. Precompute aggressively and keep the bet-size menu small.
- Feedback that sounds more certain than it is. Every non-solver number stays labeled an estimate.
- Scope creep into a full poker client. It is a study tool: no real money and no real-time
  assistance features.
