package uk.co.jpmc.services.trading.positionbook.model;

public record Position(String accountId, String securityId, long quantity) {}
