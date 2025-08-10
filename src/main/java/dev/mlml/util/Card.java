package dev.mlml.util;

import lombok.Getter;

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
