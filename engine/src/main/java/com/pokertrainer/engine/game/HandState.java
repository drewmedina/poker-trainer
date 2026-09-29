package com.pokertrainer.engine.game;

import com.pokertrainer.engine.core.Card;
import com.pokertrainer.engine.core.Deck;
import com.pokertrainer.engine.core.HandEvaluator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One no-limit hold'em hand for 2 to 6 players. Chips are ints and 100 chips is one big blind,
 * so there is no floating point anywhere in the game logic.
 *
 * <p>Heads-up follows the usual rule: the button posts the small blind and acts first before the
 * flop, last after it. With three or more players the small blind sits left of the button.
 *
 * <p>This class is mutable and not thread-safe. Callers that need to look at a decision before
 * it happens (feedback, bots) should read from it before calling {@link #apply(Action)}.
 */
public final class HandState {
    public static final int SMALL_BLIND = 50;
    public static final int BIG_BLIND = 100;

    private final int n;
    private final int button;
    private final int sbSeat;
    private final int bbSeat;
    private final int[] start;
    private final int[] stack;
    private final int[] committed;
    private final int[] contributed;
    private final boolean[] folded;
    private final boolean[] acted;
    private final int[][] hole;
    private final int[] board = new int[5];
    private int boardCount;
    private final Deck deck;

    private Street street = Street.PREFLOP;
    private int toAct = -1;
    private int lastRaiseSize = BIG_BLIND;
    private boolean terminal;
    private HandResult result;
    private final List<LoggedAction> log = new ArrayList<>();

    private HandState(int[] stacks, int button, Deck deck) {
        if (stacks.length < 2 || stacks.length > 6) {
            throw new IllegalArgumentException("Tables hold 2 to 6 players");
        }
        this.n = stacks.length;
        this.button = button;
        this.sbSeat = n == 2 ? button : (button + 1) % n;
        this.bbSeat = (sbSeat + 1) % n;
        this.start = stacks.clone();
        this.stack = stacks.clone();
        this.committed = new int[n];
        this.contributed = new int[n];
        this.folded = new boolean[n];
        this.acted = new boolean[n];
        this.hole = new int[n][2];
        this.deck = deck;
    }

    /** Posts blinds, deals hole cards and sets the first player to act. */
    public static HandState deal(int[] stacks, int button, Deck deck) {
        HandState h = new HandState(stacks, button, deck);
        h.post(h.sbSeat, SMALL_BLIND);
        h.post(h.bbSeat, BIG_BLIND);
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < h.n; i++) {
                h.hole[(h.sbSeat + i) % h.n][round] = deck.draw();
            }
        }
        int firstPreflop = h.n == 2 ? h.button : (h.bbSeat + 1) % h.n;
        h.toAct = h.canAct(firstPreflop) ? firstPreflop : h.nextActor(firstPreflop);
        if (h.toAct < 0 || h.bettingRoundComplete()) {
            h.endStreet();
        }
        return h;
    }

    // ---- reads ---------------------------------------------------------------------------

    public int players() { return n; }
    public int button() { return button; }
    public int smallBlindSeat() { return sbSeat; }
    public int bigBlindSeat() { return bbSeat; }
    public Street street() { return street; }
    public int toAct() { return terminal ? -1 : toAct; }
    public boolean isTerminal() { return terminal; }
    public HandResult result() { return result; }
    public List<LoggedAction> log() { return Collections.unmodifiableList(log); }
    public int stack(int seat) { return stack[seat]; }
    public int startingStack(int seat) { return start[seat]; }
    public int committed(int seat) { return committed[seat]; }
    public boolean folded(int seat) { return folded[seat]; }
    public boolean allIn(int seat) { return !folded[seat] && stack[seat] == 0; }
    public int[] holeCards(int seat) { return hole[seat].clone(); }
    public int[] board() { return Arrays.copyOf(board, boardCount); }
    public String position(int seat) { return Positions.label(seat, button, n); }

    /** Everything in the middle, including bets on the current street. */
    public int potTotal() {
        int sum = 0;
        for (int c : contributed) sum += c;
        return sum;
    }

    /** The pot from earlier streets, not counting bets in front of players right now. */
    public int potBeforeStreet() {
        int sum = potTotal();
        for (int c : committed) sum -= c;
        return sum;
    }

    public int currentBet() {
        int max = 0;
        for (int c : committed) max = Math.max(max, c);
        return max;
    }

    public int toCall(int seat) {
        return Math.min(currentBet() - committed[seat], stack[seat]);
    }

    /** Players still holding cards, including the one asking. */
    public int playersInHand() {
        int count = 0;
        for (boolean f : folded) if (!f) count++;
        return count;
    }

    // ---- legal actions -------------------------------------------------------------------

    public List<LegalAction> legalActions() {
        if (terminal) return List.of();
        int seat = toAct;
        int toCall = toCall(seat);
        int cur = currentBet();
        int maxTo = committed[seat] + stack[seat];
        List<LegalAction> out = new ArrayList<>();

        if (toCall > 0) {
            out.add(new LegalAction(ActionType.FOLD, 0, "Fold", false));
            out.add(new LegalAction(ActionType.CALL, toCall, "Call", toCall >= stack[seat]));
        } else {
            out.add(new LegalAction(ActionType.CHECK, 0, "Check", false));
        }

        if (stack[seat] > toCall && someoneElseCanRespond(seat)) {
            ActionType type = cur == 0 ? ActionType.BET : ActionType.RAISE;
            int minTo = cur + Math.max(lastRaiseSize, BIG_BLIND);
            List<Integer> seen = new ArrayList<>();
            for (Sizing s : sizeMenu(seat)) {
                int target = Math.max(roundChips(s.to), minTo);
                if (target >= maxTo || seen.contains(target)) continue;
                seen.add(target);
                out.add(new LegalAction(type, target, s.label, false));
            }
            out.add(new LegalAction(type, maxTo, "All-in", true));
        }
        return out;
    }

    private record Sizing(int to, String label) {}

    /**
     * The fixed size menu. Keeping sizes to a few options keeps hands on trees a solver can
     * cover later, and keeps the action dock clean.
     */
    private List<Sizing> sizeMenu(int seat) {
        int cur = currentBet();
        int pot = potTotal();
        if (cur == 0) {
            return List.of(
                new Sizing(pot * 33 / 100, "Bet 33%"),
                new Sizing(pot * 75 / 100, "Bet 75%"),
                new Sizing(pot * 150 / 100, "Bet 150%"));
        }
        boolean unopened = street == Street.PREFLOP && cur == BIG_BLIND;
        if (unopened) {
            return toCall(seat) > 0
                ? List.of(new Sizing(250, "Raise"))
                : List.of(new Sizing(400, "Raise"));
        }
        int callAmount = cur - committed[seat];
        int potSizedTo = cur + pot + callAmount;
        return List.of(new Sizing(cur * 3, "Raise"), new Sizing(potSizedTo, "Raise pot"));
    }

    private static int roundChips(int chips) {
        return Math.max(10, Math.round(chips / 10f) * 10);
    }

    private boolean someoneElseCanRespond(int seat) {
        for (int s = 0; s < n; s++) {
            if (s != seat && canAct(s)) return true;
        }
        return false;
    }

    // ---- applying actions ----------------------------------------------------------------

    public void apply(Action action) {
        if (terminal) throw new IllegalStateException("Hand is over");
        int seat = toAct;
        int toCall = toCall(seat);

        switch (action.type()) {
            case FOLD -> {
                if (toCall == 0) throw new IllegalArgumentException("Nothing to fold to, check instead");
                folded[seat] = true;
                log.add(new LoggedAction(seat, street, ActionType.FOLD, 0, committed[seat], false));
            }
            case CHECK -> {
                if (toCall != 0) throw new IllegalArgumentException("Cannot check facing a bet");
                log.add(new LoggedAction(seat, street, ActionType.CHECK, 0, committed[seat], false));
            }
            case CALL -> {
                if (toCall == 0) throw new IllegalArgumentException("Nothing to call");
                put(seat, toCall);
                log.add(new LoggedAction(seat, street, ActionType.CALL, toCall, committed[seat], stack[seat] == 0));
            }
            case BET, RAISE -> {
                int cur = currentBet();
                int target = action.amount();
                int maxTo = committed[seat] + stack[seat];
                if (stack[seat] <= toCall || !someoneElseCanRespond(seat)) {
                    throw new IllegalArgumentException("Raising is not allowed here");
                }
                if (target <= cur || target > maxTo) {
                    throw new IllegalArgumentException("Bad bet size " + target);
                }
                int increment = target - cur;
                int minIncrement = Math.max(lastRaiseSize, BIG_BLIND);
                if (increment < minIncrement && target != maxTo) {
                    throw new IllegalArgumentException("Raise must be at least " + (cur + minIncrement));
                }
                int added = target - committed[seat];
                put(seat, added);
                if (increment >= minIncrement) {
                    lastRaiseSize = increment;
                }
                for (int s = 0; s < n; s++) acted[s] = false;
                ActionType type = cur == 0 ? ActionType.BET : ActionType.RAISE;
                log.add(new LoggedAction(seat, street, type, added, committed[seat], stack[seat] == 0));
            }
            default -> throw new IllegalArgumentException("Not a player action: " + action.type());
        }
        acted[seat] = true;
        advance();
    }

    private void post(int seat, int amount) {
        int added = Math.min(amount, stack[seat]);
        put(seat, added);
        log.add(new LoggedAction(seat, street, ActionType.POST, added, committed[seat], stack[seat] == 0));
    }

    private void put(int seat, int chips) {
        stack[seat] -= chips;
        committed[seat] += chips;
        contributed[seat] += chips;
    }

    private boolean canAct(int seat) {
        return !folded[seat] && stack[seat] > 0;
    }

    private int nextActor(int from) {
        for (int i = 1; i <= n; i++) {
            int s = (from + i) % n;
            if (canAct(s)) return s;
        }
        return -1;
    }

    private boolean bettingRoundComplete() {
        int cur = currentBet();
        for (int s = 0; s < n; s++) {
            if (!canAct(s)) continue;
            if (!acted[s] || committed[s] < cur) return false;
        }
        return true;
    }

    private void advance() {
        if (playersInHand() == 1) {
            awardUncontested();
            return;
        }
        if (bettingRoundComplete()) {
            endStreet();
            return;
        }
        toAct = nextActor(toAct);
    }

    private void endStreet() {
        Arrays.fill(committed, 0);
        Arrays.fill(acted, false);
        lastRaiseSize = BIG_BLIND;

        int actors = 0;
        for (int s = 0; s < n; s++) if (canAct(s)) actors++;

        if (street == Street.RIVER) {
            showdown();
            return;
        }
        if (actors <= 1) {
            while (boardCount < 5) board[boardCount++] = deck.draw();
            street = Street.RIVER;
            showdown();
            return;
        }
        street = street.next();
        int cards = street == Street.FLOP ? 3 : 1;
        for (int i = 0; i < cards; i++) board[boardCount++] = deck.draw();
        toAct = nextActor(button);
    }

    private void awardUncontested() {
        int winner = -1;
        for (int s = 0; s < n; s++) if (!folded[s]) winner = s;
        int[] won = new int[n];
        won[winner] = potTotal();
        finish(won, List.of(winner), false, new String[n]);
    }

    /** Splits the pot into main and side pots by contribution level and awards each one. */
    private void showdown() {
        int[] score = new int[n];
        String[] names = new String[n];
        for (int s = 0; s < n; s++) {
            if (folded[s]) continue;
            int[] seven = new int[7];
            seven[0] = hole[s][0];
            seven[1] = hole[s][1];
            System.arraycopy(board, 0, seven, 2, 5);
            score[s] = HandEvaluator.evaluate(seven, 7);
            names[s] = HandEvaluator.describe(score[s]);
        }

        int[] levels = Arrays.stream(contributed)
            .filter(c -> c > 0)
            .distinct()
            .sorted()
            .toArray();
        int[] won = new int[n];
        List<Integer> winners = new ArrayList<>();
        int prev = 0;
        for (int level : levels) {
            int chunk = 0;
            for (int s = 0; s < n; s++) {
                chunk += Math.min(contributed[s], level) - Math.min(contributed[s], prev);
            }
            prev = level;
            if (chunk == 0) continue;

            int best = Integer.MIN_VALUE;
            List<Integer> eligible = new ArrayList<>();
            for (int s = 0; s < n; s++) {
                if (!folded[s] && contributed[s] >= level) {
                    if (score[s] > best) {
                        best = score[s];
                        eligible.clear();
                    }
                    if (score[s] == best) eligible.add(s);
                }
            }
            if (eligible.isEmpty()) {
                // Only folded players reached this level. Give it to the best remaining hand.
                for (int s = 0; s < n; s++) {
                    if (folded[s]) continue;
                    if (score[s] > best) {
                        best = score[s];
                        eligible.clear();
                    }
                    if (score[s] == best) eligible.add(s);
                }
            }
            eligible.sort((a, b) -> Integer.compare(Math.floorMod(a - button - 1, n), Math.floorMod(b - button - 1, n)));
            int share = chunk / eligible.size();
            int odd = chunk - share * eligible.size();
            for (int i = 0; i < eligible.size(); i++) {
                int s = eligible.get(i);
                won[s] += share + (i < odd ? 1 : 0);
                if (!winners.contains(s)) winners.add(s);
            }
        }
        finish(won, winners, true, names);
    }

    private void finish(int[] won, List<Integer> winners, boolean showdown, String[] names) {
        int[] net = new int[n];
        for (int s = 0; s < n; s++) {
            stack[s] += won[s];
            net[s] = stack[s] - start[s];
        }
        Arrays.fill(committed, 0);
        terminal = true;
        toAct = -1;
        result = new HandResult(net, won, List.copyOf(winners), showdown, names);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(street).append(" pot=").append(potTotal()).append(" board=");
        for (int c : board()) sb.append(Card.toString(c));
        for (int s = 0; s < n; s++) {
            sb.append("\n  seat ").append(s).append(' ').append(position(s))
                .append(" stack=").append(stack[s]).append(" in=").append(committed[s])
                .append(folded[s] ? " folded" : "")
                .append(s == toAct ? " <- to act" : "");
        }
        return sb.toString();
    }
}
