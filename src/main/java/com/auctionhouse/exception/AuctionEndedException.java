package com.auctionhouse.exception;

public class AuctionEndedException extends RuntimeException {

    public AuctionEndedException(String message) {
        super(message);
    }
}
