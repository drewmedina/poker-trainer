package com.pokertrainer.server.session;

public enum TableFormat {
    SIX_MAX(6), HEADS_UP(2);

    public final int seats;

    TableFormat(int seats) {
        this.seats = seats;
    }
}
