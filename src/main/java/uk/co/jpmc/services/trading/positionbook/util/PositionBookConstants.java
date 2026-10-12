package uk.co.jpmc.services.trading.positionbook.util;

public final class PositionBookConstants {

    public static final String ACCOUNT_ID_FIELD = "accountId";
    public static final String ACTION_FIELD = "action";
    public static final String EVENT_FIELD = "event";
    public static final String EVENT_ID_FIELD = "eventId";
    public static final String QUANTITY_FIELD = "quantity";
    public static final String SECURITY_ID_FIELD = "securityId";

    public static final String BUY_ACTION = "BUY";
    public static final String CANCEL_ACTION = "CANCEL";
    public static final String SELL_ACTION = "SELL";

    public static final String INVALID_ACCOUNT_ID_CODE = "INVALID_ACCOUNT_ID";
    public static final String INVALID_ACTION_CODE = "INVALID_ACTION";
    public static final String INVALID_EVENT_CODE = "INVALID_EVENT";
    public static final String INVALID_EVENT_ID_CODE = "INVALID_EVENT_ID";
    public static final String INVALID_QUANTITY_CODE = "INVALID_QUANTITY";
    public static final String INVALID_SECURITY_ID_CODE = "INVALID_SECURITY_ID";

    public static final String INVALID_ACTION_MESSAGE =
            "Action must be " + BUY_ACTION + ", " + SELL_ACTION + " or " + CANCEL_ACTION;

    public static final String TRADE_ACTION_PATTERN =
            BUY_ACTION + "|" + SELL_ACTION + "|" + CANCEL_ACTION;

    private PositionBookConstants() {}
}
