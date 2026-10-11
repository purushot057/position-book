package uk.co.jpmc.services.trading.positionbook.web.dto;

import java.util.List;

public record TradeBatchResponse(int processed, int rejected, List<TradeEventResult> results) {}
