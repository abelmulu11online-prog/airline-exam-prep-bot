package com.airlineprep.bot.user;

import org.springframework.stereotype.Component;

@Component
public class EthiopianPhoneNormalizer {
    public String normalize(String input) {
        if (input == null || input.length() > 32 || !input.matches("[+0-9 ()-]+")) {
            throw new IllegalArgumentException("Invalid Ethiopian mobile number");
        }
        String digits = input.replaceAll("[ ()-]", "");
        if (digits.matches("0[79][0-9]{8}")) return "+251" + digits.substring(1);
        if (digits.matches("251[79][0-9]{8}")) return "+" + digits;
        if (digits.matches("\\+251[79][0-9]{8}")) return digits;
        throw new IllegalArgumentException("Invalid Ethiopian mobile number");
    }
}
