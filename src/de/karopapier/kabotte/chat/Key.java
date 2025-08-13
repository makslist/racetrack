package de.karopapier.kabotte.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.mutable.FastList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Key implements Comparable<Key> {

    private String key;
    private int rank;
    private List<Decomp> decomp = new ArrayList<>();

    @SuppressWarnings("unused")
    public Key() {
    }

    public Key(String key, int rank, MutableList<Decomp> decomp) {
        this.key = key;
        this.rank = rank;
        this.decomp = decomp;
    }

    public Key(String key, int rank) {
        this.key = key;
        this.rank = rank;
        decomp = new FastList<>(0);
    }

    @SuppressWarnings("unused")
    @JsonProperty("key")
    public void setKey(String key) {
        this.key = key;
    }

    @SuppressWarnings("unused")
    @JsonProperty("rank")
    public void setRank(int rank) {
        this.rank = rank;
    }

    @SuppressWarnings("unused")
    @JsonProperty("decomb")
    public void setDecomb(Decomp[] decomb) {
        decomp.addAll(Arrays.asList(decomb));
    }

    private Key mergeDecomps(Key other) {
        rank = Math.min(rank, other.rank);
        decomp.addAll(other.decomp);
        return this;
    }

    public String key() {
        return key;
    }

    private Key addDecomp(Decomp decomp) {
        this.decomp.add(decomp);
        return this;
    }

    public List<Decomp> getDecomps() {
        return decomp;
    }

    @Override
    public String toString() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Key))
            return false;
        return key.equals(((Key) o).key);
    }

    @Override
    public int compareTo(Key key) {
        return rank - key.rank;
    }


    public static class Decomp {

        /**
         * The reassembly list
         */
        private final List<String> reasemb = new ArrayList<>();
        /**
         * The decomp pattern
         */
        private String pattern;

        @SuppressWarnings("unused")
        public Decomp() {
            pattern = null;
        }

        @SuppressWarnings("unused")
        @JsonProperty("pattern")
        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        @SuppressWarnings("unused")
        @JsonProperty("reasemb")
        public void setReasemb(String[] reasemb) {
            this.reasemb.addAll(Arrays.asList(reasemb));
        }

        public String pattern() {
            return pattern;
        }

        public String nextRule() {
            if (reasemb.isEmpty()) {
                System.out.println("No reassembly rule.");
                return null;
            }
            return reasemb.get(new Random().nextInt(reasemb.size()));
        }

        @Override
        public String toString() {
            return pattern + " " + reasemb;
        }

    }

}
