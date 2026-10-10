package uk.co.jpmc.services.trading.positionbook.web.dto;

public record PositionResponse(
        String accountId,
        String securityId,
        long quantity) {
}
