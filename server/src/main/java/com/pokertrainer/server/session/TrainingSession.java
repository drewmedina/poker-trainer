package com.pokertrainer.server.session;

import com.pokertrainer.engine.bots.BotProfile;
import com.pokertrainer.engine.bots.ProfileBot;
import com.pokertrainer.engine.core.Card;
import com.pokertrainer.engine.core.Deck;
import com.pokertrainer.engine.core.Range;
import com.pokertrainer.engine.feedback.Feedback;
import com.pokertrainer.engine.feedback.FeedbackProvider;
import com.pokertrainer.engine.feedback.Verdict;
import com.pokertrainer.engine.game.Action;
import com.pokertrainer.engine.game.ActionType;
import com.pokertrainer.engine.game.HandResult;
import com.pokertrainer.engine.game.HandState;
import com.pokertrainer.engine.game.LegalAction;
import com.pokertrainer.engine.game.LoggedAction;
import com.pokertrainer.engine.strategy.PreflopCharts;
import com.pokertrainer.server.api.Dto;
import com.pokertrainer.server.coach.CoachFeedback;
import com.pokertrainer.server.coach.CoachService;
import com.pokertrainer.server.coach.DecisionContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One player's session at one table. The hero is always seat 0 and the button moves every hand,
 * so the hero plays every position. Stacks reset to the starting depth each hand (cash-game
 * style auto top-up), which keeps every spot at a known stack depth.
 *
 * <p>Every hero decision is captured as a {@link DecisionContext} before it is applied. The coach
 * judges it in the background, and the background numbers are kept for the hand review.
 *
 * <p>Methods are synchronized so a double-clicked button cannot apply two actions at once.
 */
public final class TrainingSession {
    public static final int HERO = 0;
    private static final int KEEP_HANDS = 50;
    private static final long COACH_WAIT_SECONDS = 25;

    private final String id;
    private final TableFormat format;
    private final List<BotProfile> lineup;
    private final int stackBb;
    private final int stackChips;
    private final Random rng;
    private final FeedbackProvider numbers;
    private final CoachService coach;
    private final int firstButton;

    private int handNumber;
    private HandState hand;
    private boolean settled;
    private double netBb;
    private final Map<Integer, HandRecord> history = new LinkedHashMap<>();
    // Running totals for the whole session. history only keeps recent hands for review.
    private final AtomicInteger decisionCount = new AtomicInteger();
    private final AtomicInteger goodCount = new AtomicInteger();
    private final AtomicInteger mistakeCount = new AtomicInteger();

    private record DecisionRecord(int index, DecisionContext context, CompletableFuture<CoachFeedback> coach) {}

    private static final class HandRecord {
        final int number;
        final List<String> heroCards;
        final List<DecisionRecord> decisions = new ArrayList<>();
        List<String> finalBoard = List.of();
        Dto.ResultView result;

        HandRecord(int number, List<String> heroCards) {
            this.number = number;
            this.heroCards = heroCards;
        }
    }

    public TrainingSession(String id, TableFormat format, List<BotProfile> lineup, int stackBb,
                           Random rng, FeedbackProvider numbers, CoachService coach) {
        if (lineup.size() != format.seats - 1) {
            throw new IllegalArgumentException("Lineup does not fit the table");
        }
        this.id = id;
        this.format = format;
        this.lineup = lineup;
        this.stackBb = stackBb;
        this.stackChips = stackBb * HandState.BIG_BLIND;
        this.rng = rng;
        this.numbers = numbers;
        this.coach = coach;
        this.firstButton = rng.nextInt(format.seats);
    }

    public String id() {
        return id;
    }

    // ---- playing -------------------------------------------------------------------------

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
        history.put(handNumber, new HandRecord(handNumber, cards(hand.holeCards(HERO))));
        while (history.size() > KEEP_HANDS) history.remove(history.keySet().iterator().next());
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
        LegalAction matched = hand.legalActions().stream()
            .filter(a -> a.type() == type && (a.amount() == request.amount() || type == ActionType.FOLD
                || type == ActionType.CHECK || type == ActionType.CALL))
            .findFirst()
            .orElse(null);
        String label = matched != null ? optionLabel(matched) : describeRaw(action);

        Feedback math = numbers.evaluate(hand, action);
        DecisionContext context = buildContext(action, label, math);

        hand.apply(action); // throws IllegalArgumentException for anything illegal; nothing recorded yet

        HandRecord record = history.get(handNumber);
        int index = record.decisions.size();
        CompletableFuture<CoachFeedback> feedback = coach.submit(context);
        decisionCount.incrementAndGet();
        feedback.thenAccept(f -> {
            if (f.verdict() == Verdict.GOOD) goodCount.incrementAndGet();
            if (f.verdict() == Verdict.MISTAKE) mistakeCount.incrementAndGet();
        });
        record.decisions.add(new DecisionRecord(index, context, feedback));

        runBots();
        settleIfOver();
        return new Dto.ActionResult(view(), new Dto.DecisionView(handNumber, index, context.street(), label));
    }

    // ---- coach and review ----------------------------------------------------------------

    /** Coach feedback for one decision. Waits for the coach if it is still thinking. */
    public Dto.CoachView coach(int hand, int index) {
        DecisionRecord d;
        synchronized (this) {
            HandRecord h = history.get(hand);
            if (h == null || index < 0 || index >= h.decisions.size()) {
                throw new NoSuchElementException("No decision " + index + " in hand " + hand);
            }
            d = h.decisions.get(index);
        }
        return toView(await(d));
    }

    /** Every decision of a hand with the coach's verdict and the background numbers. */
    public Dto.ReviewView review(int handNo) {
        HandRecord h;
        List<DecisionRecord> decisions;
        List<String> board;
        boolean finished;
        synchronized (this) {
            h = history.get(handNo);
            if (h == null) throw new NoSuchElementException("No hand " + handNo);
            decisions = List.copyOf(h.decisions);
            finished = h.result != null;
            board = finished ? h.finalBoard : cards(hand.board());
        }
        List<Dto.DecisionReview> out = new ArrayList<>();
        for (DecisionRecord d : decisions) {
            DecisionContext c = d.context();
            DecisionContext.Reference r = c.reference();
            out.add(new Dto.DecisionReview(d.index(), c.street(), c.heroPosition(), c.board(), c.potBb(), c.toCallBb(),
                c.playersInHand(), c.actionLabel(), c.options(), toView(await(d)),
                new Dto.NumbersView(round2(r.equity()), round2(r.potOdds()), round1(c.spr()), r.chartPosition(),
                    r.chartOpens(), round2(r.chartShare()), r.mathVerdict(), round2(r.mathEvLossBb()), r.mathNote())));
        }
        return new Dto.ReviewView(handNo, finished, h.heroCards, board, h.result, out);
    }

    public synchronized Dto.SessionView view() {
        return new Dto.SessionView(id, format.name(), handNumber, round1(netBb), decisionCount.get(), goodCount.get(),
            mistakeCount.get(), coach.aiEnabled(), hand == null ? null : handView());
    }

    private CoachFeedback await(DecisionRecord d) {
        try {
            return d.coach().get(COACH_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the coach");
        } catch (Exception e) {
            throw new IllegalStateException("The coach did not answer in time");
        }
    }

    private static Dto.CoachView toView(CoachFeedback f) {
        return new Dto.CoachView(f.verdict().name(), f.headline(), f.explanation(), f.tip(), f.source());
    }

    // ---- decision context ------------------------------------------------------------------

    private DecisionContext buildContext(Action action, String label, Feedback math) {
        int seat = HERO;
        String street = hand.street().name();
        double pot = bb(hand.potTotal());
        double toCall = bb(hand.toCall(seat));

        int maxOpp = 0;
        List<DecisionContext.Opponent> opponents = new ArrayList<>();
        for (int s = 0; s < hand.players(); s++) {
            if (s == HERO || hand.folded(s)) continue;
            BotProfile b = lineup.get(s - 1);
            maxOpp = Math.max(maxOpp, hand.stack(s) + hand.committed(s));
            opponents.add(new DecisionContext.Opponent(b.displayName, hand.position(s), b.style, b.description,
                bb(hand.stack(s)), lastAction(s)));
        }
        int effective = Math.min(hand.stack(seat) + hand.committed(seat), maxOpp) - hand.committed(seat);
        double spr = hand.potTotal() == 0 ? 0 : effective / (double) hand.potTotal();

        boolean unopened = hand.street() == com.pokertrainer.engine.game.Street.PREFLOP
            && seat != hand.bigBlindSeat()
            && hand.log().stream().allMatch(a -> a.type() == ActionType.POST || a.type() == ActionType.FOLD);

        String chartPos = null;
        Boolean chartOpens = null;
        double chartShare = 0;
        if (unopened) {
            Optional<Range> chart = PreflopCharts.openingRange(hand.position(seat), hand.players());
            if (chart.isPresent()) {
                int[] hole = hand.holeCards(seat);
                chartPos = hand.position(seat);
                chartOpens = PreflopCharts.opens(chart.get(), hole[0], hole[1]);
                chartShare = PreflopCharts.share(chart.get());
            }
        }

        String mathVerdict = null;
        String mathNote = null;
        double mathLoss = 0;
        if (math.verdict() != Verdict.INFO) {
            mathVerdict = math.verdict().name();
            mathLoss = math.evLossBb();
            mathNote = math.detail() + " " + switch (math.verdict()) {
                case GOOD -> "The choice fits the price.";
                case INACCURACY -> "The choice is slightly off for the price.";
                default -> action.type() == ActionType.CALL ? "By this check, the call loses money." : "By this check, the fold gives up value.";
            };
        }

        List<String> historyLines = new ArrayList<>();
        for (LoggedAction a : hand.log()) {
            historyLines.add(cap(a.street().name().toLowerCase()) + ": " + name(a.seat()) + " (" + hand.position(a.seat()) + ") " + verb(a));
        }
        List<String> options = hand.legalActions().stream().map(TrainingSession::optionLabel).toList();

        DecisionContext.Reference ref = new DecisionContext.Reference(math.equity(), math.potOdds(), chartPos,
            chartOpens, chartShare, mathVerdict, mathLoss, mathNote);
        return new DecisionContext(format == TableFormat.SIX_MAX ? "6-max" : "heads-up", stackBb,
            hand.playersInHand(), street, hand.position(seat), cards(hand.holeCards(seat)), cards(hand.board()),
            pot, toCall, bb(hand.stack(seat)), spr, unopened, opponents, historyLines, options,
            action.type().name(), label, ref);
    }

    private static String optionLabel(LegalAction a) {
        if (a.allIn()) return "All-in " + fmt(a.amount());
        return switch (a.type()) {
            case FOLD -> "Fold";
            case CHECK -> "Check";
            case CALL -> "Call " + fmt(a.amount());
            case BET -> a.label() + " (" + fmt(a.amount()) + ")";
            case RAISE -> (a.label().equals("Raise pot") ? "Raise pot to " : "Raise to ") + fmt(a.amount());
            case POST -> "Post";
        };
    }

    private String describeRaw(Action action) {
        return switch (action.type()) {
            case BET -> "Bet " + fmt(action.amount());
            case RAISE -> "Raise to " + fmt(action.amount());
            default -> cap(action.type().name().toLowerCase());
        };
    }

    // ---- internals -----------------------------------------------------------------------

    private void settleIfOver() {
        if (hand.isTerminal() && !settled) {
            netBb += hand.result().net()[HERO] / (double) HandState.BIG_BLIND;
            HandRecord rec = history.get(handNumber);
            rec.finalBoard = cards(hand.board());
            rec.result = resultView(hand.result());
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

    private Dto.ResultView resultView(HandResult result) {
        List<String> names = result.winners().stream().map(this::name).toList();
        boolean you = names.size() == 1 && names.get(0).equals("You");
        String summary = String.join(" and ", names) + (names.size() == 1 && !you ? " wins " : " win ")
            + fmt(Arrays.stream(result.won()).sum()) + "bb";
        return new Dto.ResultView(result.showdown(), result.winners(), bb(result.net()[HERO]), summary);
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

        return new Dto.HandView(handNumber, hand.street().name(), bb(hand.potTotal()), bb(hand.potBeforeStreet()),
            cards(hand.board()), seats, HERO, hand.toAct(), legal, log, result == null ? null : resultView(result));
    }

    /** Short status for a seat, like "Bets 2.7", taken from its latest action this street. */
    private String lastAction(int seat) {
        if (hand.folded(seat)) return "Folded";
        List<LoggedAction> log = hand.log();
        for (int i = log.size() - 1; i >= 0; i--) {
            LoggedAction a = log.get(i);
            if (a.street() != hand.street() && !hand.isTerminal()) break;
            if (a.seat() == seat) return cap(verb(a));
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

    private static List<String> cards(int[] cs) {
        List<String> out = new ArrayList<>();
        for (int c : cs) out.add(Card.toString(c));
        return out;
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
