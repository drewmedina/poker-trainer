package com.pokertrainer.server.session;

import com.pokertrainer.engine.bots.BotProfile;
import com.pokertrainer.engine.bots.ProfileBot;
import com.pokertrainer.engine.core.Card;
import com.pokertrainer.engine.core.Deck;
import com.pokertrainer.engine.feedback.Feedback;
import com.pokertrainer.engine.feedback.FeedbackProvider;
import com.pokertrainer.engine.feedback.Verdict;
import com.pokertrainer.engine.game.Action;
import com.pokertrainer.engine.game.ActionType;
import com.pokertrainer.engine.game.HandResult;
import com.pokertrainer.engine.game.HandState;
import com.pokertrainer.engine.game.LegalAction;
import com.pokertrainer.engine.game.LoggedAction;
import com.pokertrainer.server.api.Dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * One player's session at one table. The hero is always seat 0 and the button moves every hand,
 * so the hero plays every position. Stacks reset to the starting depth each hand (cash-game
 * style auto top-up), which keeps every spot at a known stack depth for the solver later.
 *
 * <p>Methods are synchronized so a double-clicked button cannot apply two actions at once.
 */
public final class TrainingSession {
    public static final int HERO = 0;

    private final String id;
    private final TableFormat format;
    private final List<BotProfile> lineup;
    private final int stackChips;
    private final Random rng;
    private final FeedbackProvider feedback;
    private final int firstButton;

    private int handNumber;
    private HandState hand;
    private double netBb;
    private double evLostBb;
    private int decisions;
    private int goodDecisions;
    private int mistakes;

    public TrainingSession(String id, TableFormat format, List<BotProfile> lineup, int stackBb,
                           Random rng, FeedbackProvider feedback) {
        if (lineup.size() != format.seats - 1) {
            throw new IllegalArgumentException("Lineup does not fit the table");
        }
        this.id = id;
        this.format = format;
        this.lineup = lineup;
        this.stackChips = stackBb * HandState.BIG_BLIND;
        this.rng = rng;
        this.feedback = feedback;
        this.firstButton = rng.nextInt(format.seats);
    }

    public String id() {
        return id;
    }

    public synchronized Dto.SessionView dealNext() {
        if (hand != null && !hand.isTerminal()) {
            throw new IllegalStateException("Finish the current hand first");
        }
        handNumber++;
        int[] stacks = new int[format.seats];
        Arrays.fill(stacks, stackChips);
        int button = (firstButton + handNumber - 1) % format.seats;
        hand = HandState.deal(stacks, button, new Deck(rng));
        settled = false;
        runBots();
        settleIfOver();
        return view();
    }

    public synchronized Dto.ActionResult act(Dto.ActionRequest request) {
        if (hand == null || hand.isTerminal()) throw new IllegalStateException("No hand in progress");
        if (hand.toAct() != HERO) throw new IllegalStateException("It is not your turn");

        ActionType type;
        try {
            type = ActionType.valueOf(request.type());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown action: " + request.type());
        }
        Action action = new Action(type, request.amount());
        String label = describeHeroAction(action);

        Feedback fb = feedback.evaluate(hand, action);
        hand.apply(action); // throws IllegalArgumentException for anything illegal; nothing recorded yet

        decisions++;
        if (fb.verdict() != Verdict.INFO) {
            evLostBb += fb.evLossBb();
            if (fb.verdict() == Verdict.GOOD) goodDecisions++;
            if (fb.verdict() == Verdict.MISTAKE) mistakes++;
        }

        runBots();
        settleIfOver();
        return new Dto.ActionResult(view(), toView(fb, label));
    }

    public synchronized Dto.SessionView view() {
        return new Dto.SessionView(id, format.name(), handNumber, round1(netBb), round1(evLostBb),
            decisions, goodDecisions, mistakes, hand == null ? null : handView());
    }

    // ---- internals -----------------------------------------------------------------------

    /** True once the current hand's result has been added to the session totals. */
    private boolean settled;

    private void settleIfOver() {
        if (hand.isTerminal() && !settled) {
            netBb += hand.result().net()[HERO] / (double) HandState.BIG_BLIND;
            settled = true;
        }
    }

    private void runBots() {
        while (!hand.isTerminal() && hand.toAct() != HERO) {
            int seat = hand.toAct();
            ProfileBot bot = new ProfileBot(lineup.get(seat - 1));
            hand.apply(bot.decide(hand, rng));
        }
    }

    private String name(int seat) {
        return seat == HERO ? "You" : lineup.get(seat - 1).displayName;
    }

    private Dto.HandView handView() {
        HandResult result = hand.result();
        boolean revealAll = result != null && result.showdown();
        List<Dto.SeatView> seats = new ArrayList<>();
        for (int s = 0; s < hand.players(); s++) {
            BotProfile bot = s == HERO ? null : lineup.get(s - 1);
            List<String> cards = (s == HERO || (revealAll && !hand.folded(s))) ? cards(hand.holeCards(s)) : null;
            String handName = result != null && result.handNames()[s] != null ? result.handNames()[s] : null;
            seats.add(new Dto.SeatView(
                s, name(s), bot == null ? null : bot.id, bot == null ? null : bot.style,
                hand.position(s), bb(hand.stack(s)), bb(hand.committed(s)),
                hand.folded(s), hand.allIn(s), s == hand.button(), s == HERO,
                cards, lastAction(s), handName));
        }

        List<Dto.ActionView> legal = new ArrayList<>();
        if (!hand.isTerminal() && hand.toAct() == HERO) {
            for (LegalAction a : hand.legalActions()) {
                legal.add(new Dto.ActionView(a.type().name(), a.amount(), a.label(), bb(a.amount()), a.allIn()));
            }
        }

        List<Dto.LogView> log = new ArrayList<>();
        for (LoggedAction a : hand.log()) {
            log.add(new Dto.LogView(a.seat(), name(a.seat()), a.street().name(), name(a.seat()) + " " + verb(a)));
        }

        Dto.ResultView resultView = null;
        if (result != null) {
            List<String> names = result.winners().stream().map(this::name).toList();
            String summary = String.join(" and ", names) + (names.size() == 1 && !names.get(0).equals("You") ? " wins" : " win")
                + " " + fmt(Arrays.stream(result.won()).sum()) + "bb";
            resultView = new Dto.ResultView(result.showdown(), result.winners(), bb(result.net()[HERO]), summary);
        }

        return new Dto.HandView(handNumber, hand.street().name(), bb(hand.potTotal()), bb(hand.potBeforeStreet()),
            cards(hand.board()), seats, HERO, hand.toAct(), legal, log, resultView);
    }

    /** Short status for a seat, like "Bets 2.7", taken from its latest action this street. */
    private String lastAction(int seat) {
        if (hand.folded(seat)) return "Folded";
        List<LoggedAction> log = hand.log();
        for (int i = log.size() - 1; i >= 0; i--) {
            LoggedAction a = log.get(i);
            if (a.street() != hand.street() && !hand.isTerminal()) break;
            if (a.seat() == seat) {
                String v = verb(a);
                return Character.toUpperCase(v.charAt(0)) + v.substring(1);
            }
        }
        return null;
    }

    private static String verb(LoggedAction a) {
        String allIn = a.allIn() ? " (all-in)" : "";
        return switch (a.type()) {
            case POST -> "posts " + fmt(a.added());
            case FOLD -> "folds";
            case CHECK -> "checks";
            case CALL -> "calls " + fmt(a.added()) + allIn;
            case BET -> "bets " + fmt(a.streetTotal()) + allIn;
            case RAISE -> "raises to " + fmt(a.streetTotal()) + allIn;
        };
    }

    private String describeHeroAction(Action action) {
        return switch (action.type()) {
            case FOLD -> "Fold";
            case CHECK -> "Check";
            case CALL -> "Call " + fmt(hand.toCall(HERO));
            case BET -> "Bet " + fmt(action.amount());
            case RAISE -> "Raise to " + fmt(action.amount());
            case POST -> "Post";
        };
    }

    private static Dto.FeedbackView toView(Feedback f, String actionLabel) {
        return new Dto.FeedbackView(f.verdict().name(), round2(f.evLossBb()), f.headline(), f.detail(),
            round2(f.equity()), round2(f.potOdds()), f.frequencies(), f.multiway(), f.estimate(), f.source(), actionLabel);
    }

    private static List<String> cards(int[] cs) {
        List<String> out = new ArrayList<>();
        for (int c : cs) out.add(Card.toString(c));
        return out;
    }

    private static double bb(int chips) {
        return chips / (double) HandState.BIG_BLIND;
    }

    /** "2.7" or "100", for text. */
    static String fmt(int chips) {
        return chips % HandState.BIG_BLIND == 0
            ? String.valueOf(chips / HandState.BIG_BLIND)
            : String.format("%.1f", chips / (double) HandState.BIG_BLIND);
    }

    private static double round1(double x) {
        return Math.round(x * 10) / 10.0;
    }

    private static double round2(double x) {
        return Math.round(x * 100) / 100.0;
    }
}
