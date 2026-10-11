package uk.co.jpmc.services.trading.positionbook.web.dto;

public record TradePositionResponse(String accountId, String securityId, long quantity) {}
