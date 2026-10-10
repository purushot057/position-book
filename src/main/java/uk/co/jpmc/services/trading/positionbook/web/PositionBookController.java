package uk.co.jpmc.services.trading.positionbook.web;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.co.jpmc.services.trading.positionbook.web.dto.PositionResponse;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeBatchResponse;
import uk.co.jpmc.services.trading.positionbook.web.dto.TradeEventRequest;

import java.util.List;


@RestController
public class PositionBookController {

    private static final String TRADE_EVENTS_PATH = "/trade-events";
    private static final String POSITIONS_PATH = "/positions";
    private static final String ACCOUNT_ID_QUERY_PARAM = "accountId";
    private static final String SECURITY_ID_QUERY_PARAM = "securityId";

    @PostMapping(TRADE_EVENTS_PATH)
    public TradeBatchResponse processTradeEvents(
            @RequestBody List<TradeEventRequest> events) {

        return new TradeBatchResponse(events.size());
    }

    @GetMapping(POSITIONS_PATH)
    public PositionResponse getPosition(
            @RequestParam(ACCOUNT_ID_QUERY_PARAM) String accountId,
            @RequestParam(SECURITY_ID_QUERY_PARAM) String securityId) {

        return new PositionResponse(accountId, securityId, 150L);
    }
}
