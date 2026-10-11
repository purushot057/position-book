package uk.co.jpmc.services.trading.positionbook.model;

public record TradeEvent(
        Long eventId, String action, String accountId, String securityId, Long quantity) {}
