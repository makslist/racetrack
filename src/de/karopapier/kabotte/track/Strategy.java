package de.karopapier.kabotte.track;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.karopapier.api.Move;
import de.karopapier.api.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class Strategy {

    private static final NumberFormat TWO_DECIMAL;

    static {
        DecimalFormatSymbols decimalSymbols = DecimalFormatSymbols.getInstance();
        decimalSymbols.setDecimalSeparator('.');
        TWO_DECIMAL = new DecimalFormat("0.##", decimalSymbols);
    }

    protected final Map<Player, Integer> playerIdx = new LinkedHashMap<>();
    protected final Player player;

    private final Map<Player, Integer> playerFinishRound = new LinkedHashMap<>();
    private final int minGameRounds;

    private Score maxDepth;

    private Strategy(Player player, Map<Player, Paths> paths) {
        this.player = player;
        Comparator<Player> firstPlayerThenName = (p1, p2) -> player.equals(p1) ? -1
                : (player.equals(p2) ? 1 : p1.name().compareTo(p2.name()));

        paths.forEach((pl, path) -> playerFinishRound.put(pl, path.length()));

        int i = 0;
        for (Player p : playerFinishRound.keySet().stream().sorted(firstPlayerThenName).toList())
            playerIdx.put(p, i++);
        minGameRounds = paths.values().stream().mapToInt(Paths::length).min().orElse(100);
    }

    public static Strategy get(Player player, Map<Player, Paths> paths) {
        Map<Player, Integer> playerPathLength = new HashMap<>();
        paths.forEach((key, value) -> playerPathLength.put(key, value.length()));

        if (playerPathLength.size() >= 2) {
            List<Player> players = playerPathLength.entrySet().stream().sorted(Comparator.comparingInt(Entry::getValue))
                    .map(Entry::getKey).toList();

            Player leader = players.get(0);
            int gameLength = playerPathLength.get(leader);
            int inTheLead = Math.abs(gameLength - playerPathLength.get(players.get(1)));
            if (leader == player && inTheLead >= 1)
                return new Paranoid(player, paths);
            else if (leader != player && inTheLead >= 2)
                return new Offensive(player, paths, leader);
        }
        return new MaxN(player, paths);
    }

    private static int roundToInt(float number) {
        return number == 0 ? 0 : (number > 0 ? 1 : -1);
    }

    public final Comparator<Score> comp(int player) {
        return (o1, o2) -> {
            int pos1 = o1.getPosition(player);
            int pos2 = o2.getPosition(player);
            if (pos2 - pos1 != 0)
                return pos2 - pos1;
            else if (pos1 == 1) {
                int lead = roundToInt(o2.getLead(player) - o1.getLead(player));
                return lead != 0 ? lead : roundToInt(o1.getScore(player) - o2.getScore(player));
            } else {
                int lag = roundToInt(o2.getLag(player) - o1.getLag(player));
                return lag != 0 ? lag : roundToInt(o1.getScore(player) - o2.getScore(player));
            }
        };
    }

    public abstract Score eval(Player pl, List<Score> scores);

    public Comparator<GTS.WeightedMove> maxScore(Player player, BiFunction<Player, Move, Integer> followUps) {
        int playerId = playerIdx.get(player);
        return (o1, o2) -> {
            Score sco1 = o1.score();
            Score sco2 = o2.score();
            int pos = sco1.getPosition(playerId) - sco2.getPosition(playerId);
            if (pos != 0)
                return pos;
            else if (sco1.getPosition(playerId) == 1) {
                int lead = roundToInt(sco1.getLead(playerId) - sco2.getLead(playerId));
                if (lead != 0)
                    return lead;
                else {
                    int score = roundToInt(sco2.getScore(playerId) - sco1.getScore(playerId));
                    return score != 0 ? score : followUps.apply(player, o2.move()) - followUps.apply(player, o1.move());
                }
            } else {
                int lag = roundToInt(sco1.getLag(playerId) - sco2.getLag(playerId));
                if (lag != 0)
                    return lag;
                else {
                    int score = roundToInt(sco2.getScore(playerId) - sco1.getScore(playerId));
                    return score != 0 ? score : followUps.apply(player, o2.move()) - followUps.apply(player, o1.move());
                }
            }
        };
    }

    public Score merge(List<Score> scores) {
        if (scores.size() == 1)
            return scores.get(0).normalize();
        Score avg = new Score(playerIdx.size());
        for (int i = 0; i < avg.ratings.length; i++)
            for (Score value : scores)
                avg.ratings[i] += value.ratings[i];
        return avg.normalize();
    }

    public Score maxDepth() {
        Score score = new Score(playerIdx.size());
        playerFinishRound.forEach((key, value) -> score.ratings[playerIdx.get(key)] = (value == minGameRounds ? 1 : 0));
        return score.normalize();
    }

    public Score gameEnd(Turn round) {
        Score score = new Score(playerIdx.size());
        for (Player p : playerIdx.keySet())
            score.ratings[playerIdx.get(p)] = round.isBlocked(p) ? 0f : 1f;
        return score.normalize();
    }

    public Score block(Player player, Score score) {
        score.ratings[playerIdx.get(player)] = 0;
        return score;
    }

    public Player cannotBeCaught(Collection<Score> scores) {
        for (Player player : playerIdx.keySet())
            if (!player.equals(this.player)) {
                double sum = scores.stream().mapToDouble(s -> s.ratings[playerIdx.get(player)]).sum();
                if (sum == scores.size()) // if player wins in all cases
                    return player;
            }
        return null;
    }

    public List<Player> getPlayers() {
        return playerIdx.keySet().stream().toList();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " (" + playerIdx.keySet().stream().map(p -> p.name() + "[" + playerFinishRound.get(p) + "]").collect(Collectors.joining(", ", "", "")) + ")";
    }

    public static class MaxN extends Strategy {
        private MaxN(Player player, Map<Player, Paths> paths) {
            super(player, paths);
        }

        @Override
        public Score eval(Player pl, List<Score> scores) {
            if (scores.size() == 1)
                return scores.get(0);
            return scores.stream().max(comp(playerIdx.get(pl))).get();
        }
    }

    public static class Paranoid extends Strategy {
        private Paranoid(Player player, Map<Player, Paths> paths) {
            super(player, paths);
        }

        @Override
        public Score eval(Player pl, List<Score> scores) {
            if (pl != player)
                return scores.stream().min(comp(playerIdx.get(player))).get();
            else
                return scores.stream().max(comp(playerIdx.get(pl))).get();
        }
    }

    public static class Offensive extends Strategy {
        private final Player leader;

        private Offensive(Player player, Map<Player, Paths> paths, Player leader) {
            super(player, paths);
            this.leader = leader;
        }

        @Override
        public Score eval(Player pl, List<Score> scores) {
            if (pl == player)
                return scores.stream().min(comp(playerIdx.get(leader))).get();
            else
                return scores.stream().max(comp(playerIdx.get(pl))).get();
        }
    }

    public static class Score {

        private float[] ratings;

        private Score() {
            ratings = new float[0];
        }

        public Score(int players) {
            ratings = new float[players];
        }

        @JsonProperty("rating")
        public float[] ratings() {
            return ratings;
        }

        @JsonProperty("rating")
        public void setRatings(float[] ratings) {
            this.ratings = ratings;
        }

        @Override
        public String toString() {
            return IntStream.range(0, ratings.length).mapToObj(i -> TWO_DECIMAL.format(ratings[i]))
                    .collect(Collectors.joining(", ", "[", "]"));
        }

        private int getPosition(int player) {
            float playerRating = ratings[player];
            int position = 1;
            for (int i = 0; i < ratings.length; i++)
                if (player != i && ratings[i] > playerRating)
                    position++;
            return position;
        }

        private float getScore(int player) {
            return ratings[player];
        }

        private float getLead(int player) {
            float sucRating = 0;
            for (int i = 0; i < ratings.length; i++)
                if (player != i && ratings[i] > sucRating)
                    sucRating = ratings[i];
            return sucRating - ratings[player];
        }

        private float getLag(int player) {
            float playerRating = ratings[player];
            float predRating = 1;
            for (int i = 0; i < ratings.length; i++)
                if (player != i && ratings[i] > playerRating && ratings[i] < predRating)
                    predRating = ratings[i];
            return predRating - playerRating;
        }

        public Score normalize() {
            float sum = 0f;
            for (float rating : ratings)
                sum += rating;
            if (sum != 1f)
                for (int i = 0; i < ratings.length; i++)
                    ratings[i] = sum != 0 ? ratings[i] / sum : 1f / ratings.length;
            return this;
        }

        public boolean equals(Object obj) {
            return obj instanceof Score score && Arrays.equals(ratings, score.ratings);
        }

    }

}
