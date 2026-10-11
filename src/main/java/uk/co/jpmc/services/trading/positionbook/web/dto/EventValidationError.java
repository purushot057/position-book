package uk.co.jpmc.services.trading.positionbook.web.dto;

public record EventValidationError(String field, String code, String message) {}
