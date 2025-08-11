package dev.mlml.util;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CardDeck {
    @Getter
    public enum Rank {
        TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9), TEN(10),
        JACK(11), QUEEN(12), KING(13), ACE(14);

        private final int value;

        Rank(int value) {
            this.value = value;
        }
    }


    public record Card(Suit suit, Rank rank) {
        @Override
        public String toString() {
            if (rank.ordinal() < 9) {
                return "[" + (rank.ordinal() + 2) + suit.getSymbol() + "]";
            } else if (rank == Rank.ACE) {
                return "[A" + suit.getSymbol() + "]";
            } else {
                String rankStr = switch (rank) {
                    case JACK -> "J";
                    case QUEEN -> "Q";
                    case KING -> "K";
                    default -> "";
                };
                return "[" + rankStr + suit.getSymbol() + "]";
            }
        }
    }


    private List<Card> cards = new ArrayList<>();

    public CardDeck() {
        initialize();
    }

    public void initialize() {
        cards.clear();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
        Collections.shuffle(cards);
    }

    public Card drawCard() {
        if (cards.isEmpty()) {
            initialize();
        }
        return cards.remove(0);
    }

    public int size() {
        return cards.size();
    }
}