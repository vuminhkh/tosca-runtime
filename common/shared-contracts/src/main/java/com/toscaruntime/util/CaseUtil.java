package com.toscaruntime.util;

public class CaseUtil {

    public static String lowerUnderscoreToCamelCase(String input) {
        StringBuilder camelCase = new StringBuilder();
        boolean toUpperNextChar = false;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '_') {
                toUpperNextChar = true;
            } else if (toUpperNextChar) {
                camelCase.append(Character.toUpperCase(input.charAt(i)));
                toUpperNextChar = false;
            } else {
                camelCase.append(input.charAt(i));
            }
        }
        return camelCase.toString();
    }
}
