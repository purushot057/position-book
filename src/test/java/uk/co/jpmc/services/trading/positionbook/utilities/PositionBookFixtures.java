package uk.co.jpmc.services.trading.positionbook.utilities;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class PositionBookFixtures {

    public static String tradeEventsValidJson() {
        return """
                [
                  {
                    "eventId": 1,
                    "action": "BUY",
                    "accountId": "ACC1",
                    "securityId": "SEC1",
                    "quantity": 100
                  },
                  {
                    "eventId": 2,
                    "action": "BUY",
                    "accountId": "ACC1",
                    "securityId": "SEC1",
                    "quantity": 50
                  }
                ]
                """;
    }
}
