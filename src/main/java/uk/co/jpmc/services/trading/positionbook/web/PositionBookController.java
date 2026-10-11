package uk.co.jpmc.services.trading.positionbook.web;

import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.co.jpmc.services.trading.positionbook.service.TradeEventService;
import uk.co.jpmc.services.trading.positionbook.util.PositionBookConstants;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeBatchResponse;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventRequest;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradePositionResponse;

@RestController
@AllArgsConstructor
public class PositionBookController {

    private static final String TRADE_EVENTS_PATH = "/trade-events";
    private static final String POSITIONS_PATH = "/positions";
    private final TradeEventService tradeEventService;

    @PostMapping(TRADE_EVENTS_PATH)
    public TradeBatchResponse processTradeEvents(@RequestBody List<TradeEventRequest> events) {

        return tradeEventService.processTradeEvents(events);
    }

    @GetMapping(POSITIONS_PATH)
    public TradePositionResponse getPosition(
            @RequestParam(PositionBookConstants.ACCOUNT_ID_FIELD) String accountId,
            @RequestParam(PositionBookConstants.SECURITY_ID_FIELD) String securityId) {

        return tradeEventService.retrievePosition(accountId, securityId);
    }
}
