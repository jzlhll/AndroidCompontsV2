package com.au.module_android.utils;

import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则匹配的规则
 */
public class PatternEmailUtils {
    private PatternEmailUtils() {}

    public static boolean match(String email) {
        try {
            if (TextUtils.isEmpty(email)) {
                return false;
            } else {
                String[] splits = email.split("@");
                if (splits.length != 2) {
                    return false;
                }

                if (splits[0].isEmpty() || splits[0].length() > 64 || splits[0].trim().isEmpty()) {
                    return false;
                }
                if (splits[1].isEmpty() || splits[1].length() > 255 || splits[1].trim().isEmpty()) {
                    return false;
                }

                String emailPattern = "^.+@.+\\.[A-Za-z]+$";
                Pattern pattern = Pattern.compile(emailPattern);
                Matcher matcher = pattern.matcher(email);
                return matcher.matches();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
