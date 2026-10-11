package uk.co.jpmc.services.trading.positionbook.util;

public final class PositionBookConstants {

    public static final String ACCOUNT_ID_FIELD = "accountId";
    public static final String BUY_ACTION = "BUY";
    public static final String CANCEL_ACTION = "CANCEL";
    public static final String SELL_ACTION = "SELL";
    public static final String INVALID_ACTION_CODE = "INVALID_ACTION";
    public static final String INVALID_ACTION_MESSAGE =
            "Action must be " + BUY_ACTION + ", " + SELL_ACTION + " or " + CANCEL_ACTION;
    public static final String SECURITY_ID_FIELD = "securityId";
    public static final String TRADE_ACTION_PATTERN =
            BUY_ACTION + "|" + SELL_ACTION + "|" + CANCEL_ACTION;

    private PositionBookConstants() {}
}
