package com.urlshortener.util;

import org.springframework.stereotype.Component;

@Component
public class Base62Encoder {

    private static final String CHARSET =
        "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;

    public String encode(long id) {
        if (id <= 0) throw new IllegalArgumentException("ID must be positive");

        StringBuilder sb = new StringBuilder();
        long num = id;

        while (num > 0) {
            sb.append(CHARSET.charAt((int)(num % BASE)));
            num /= BASE;
        }

        // remainders are collected in reverse — flip it
        return sb.reverse().toString();
    }

    public long decode(String code) {
        if (code == null || code.isBlank())
            throw new IllegalArgumentException("Code must not be blank");

        long result = 0;
        for (char c : code.toCharArray()) {
            int index = CHARSET.indexOf(c);
            if (index == -1)
                throw new IllegalArgumentException("Invalid character in code: " + c);
            result = result * BASE + index;
        }
        return result;
    }
}