package uk.co.jpmc.services.trading.positionbook.utilities;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class PositionBookFixtures {

    public static final String ACCOUNT_ID_FIELD = "accountId";
    public static final String ACCOUNT_ID = "ACC1";
    public static final String SECONDARY_ACCOUNT_ID = "ACC2";
    public static final String ACTION_FIELD = "action";
    public static final String BUY_ACTION = "BUY";
    public static final String CANCEL_ACTION = "CANCEL";
    public static final String EVENT_ID_FIELD = "eventId";
    public static final String QUANTITY_FIELD = "quantity";
    public static final String SECURITY_ID_FIELD = "securityId";
    public static final String SECURITY_ID = "SEC1";
    public static final String SECONDARY_SECURITY_ID = "SEC2";
    public static final String ALTERNATE_SECURITY_ID = "SECXYZ";
    public static final String SELL_ACTION = "SELL";
    public static final String PROCESSED_STATUS = "PROCESSED";
    public static final String REJECTED_STATUS = "REJECTED";
    public static final String INVALID_ACCOUNT_ID_CODE = "INVALID_ACCOUNT_ID";
    public static final String INVALID_ACTION_CODE = "INVALID_ACTION";
    public static final String INVALID_EVENT_ID_CODE = "INVALID_EVENT_ID";
    public static final String INVALID_QUANTITY_CODE = "INVALID_QUANTITY";
    public static final String INVALID_SECURITY_ID_CODE = "INVALID_SECURITY_ID";
    public static final String DUPLICATE_EVENT_ID_CODE = "DUPLICATE_EVENT_ID";
    public static final String POSITION_OVERFLOW_CODE = "POSITION_OVERFLOW";
    public static final String UNKNOWN_EVENT_ID_CODE = "UNKNOWN_EVENT_ID";
    public static final String TRADE_ALREADY_CANCELLED_CODE = "TRADE_ALREADY_CANCELLED";
    public static final String ACCOUNT_MISMATCH_CODE = "ACCOUNT_MISMATCH";
    public static final String NEGATIVE_POSITION_CODE = "NEGATIVE_POSITION";

    public static final String SECURITY_ID_REQUIRED_MESSAGE =
            "Security Id is required for BUY and SELL";
    public static final String SECURITY_ID_TOO_LONG_MESSAGE =
            "Security Id must not exceed 128 characters";
    public static final String SECURITY_ID_INVALID_CHARACTERS_MESSAGE =
            "Security Id may contain only letters, numbers, hyphens, underscores and dots";
    public static final String QUANTITY_REQUIRED_MESSAGE = "Quantity is required for BUY and SELL";
    public static final String QUANTITY_NOT_POSITIVE_MESSAGE =
            "Quantity must be greater than zero for BUY and SELL";

    public static String tradeEventsValidJson() {
        return """
                [
                  {
                    "id": 1,
                    "action": "%1$s",
                    "accountId": "%2$s",
                    "securityId": "%3$s",
                    "quantity": 100
                  },
                  {
                    "id": 2,
                    "action": "%1$s",
                    "accountId": "%2$s",
                    "securityId": "%3$s",
                    "quantity": 50
                  }
                ]
                """
                .formatted(BUY_ACTION, ACCOUNT_ID, SECURITY_ID);
    }
}
