package com.saritha.fashions.util;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

public class DateTimeUtils {

    private static final ZoneId IST_ZONE_ID = ZoneId.of("Asia/Kolkata");

    // ✅ Get current time in IST as java.util.Date
    public static Date getNowInIST() {
        ZonedDateTime istNow = ZonedDateTime.now(IST_ZONE_ID);
        return Date.from(istNow.toInstant());
    }

    // ✅ Get current time in IST as formatted string
    public static String getFormattedNowInIST() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        return sdf.format(new Date());
    }

    // ✅ Format any date into IST string
    public static String formatDateToIST(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        return sdf.format(date);
    }
}

