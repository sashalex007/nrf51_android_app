package com.alexandrepokhodoun.nrf51_app;

class Constants {

    // values have to be globally unique
    static final String INTENT_ACTION_DISCONNECT = BuildConfig.APPLICATION_ID + ".Disconnect";
    static final String NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID + ".Channel";
    static final String INTENT_CLASS_MAIN_ACTIVITY = BuildConfig.APPLICATION_ID + ".MainActivity";

    // values have to be unique within each app
    static final int NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001;

    public static final String KEY_cal1 = "cal1";
    public static final String KEY_cal2 = "cal2";

    public static final String KEY_nrf51addr = "rightAdr";
    public static final String KEY_duration = "duration";

    private Constants() {}
}
