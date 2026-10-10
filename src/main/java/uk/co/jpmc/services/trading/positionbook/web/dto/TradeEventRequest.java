package uk.co.jpmc.services.trading.positionbook.web.dto;

public record TradeEventRequest(
        Long eventId, String action, String accountId, String securityId, Long quantity) {}
