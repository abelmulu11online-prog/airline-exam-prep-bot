package com.airlineprep.bot.settings;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record SettingsForm(
        @NotNull @Min(0) Integer freePracticeLimit,
        @NotNull @Min(0) Integer freeMockLimit,
        @NotNull @Min(1) Integer questionsPerMock,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal lifetimePrice,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        Boolean paymentEnabled, Boolean manualPaymentEnabled,
        @NotNull @Size(max = 500) String supportInfo,
        @Min(1) @Max(1440) Integer mockDurationMinutes) {
    public SettingsForm(Integer practice,Integer mocks,Integer size,BigDecimal price,String currency,
                        Boolean payment,Boolean manual,String support) {
        this(practice,mocks,size,price,currency,payment,manual,support,null);
    }
    public SettingsForm {
        paymentEnabled = Boolean.TRUE.equals(paymentEnabled);
        manualPaymentEnabled = Boolean.TRUE.equals(manualPaymentEnabled);
    }
    public static SettingsForm from(AppSettings s) {
        return new SettingsForm(s.getFreePracticeLimit(), s.getFreeMockLimit(), s.getQuestionsPerMock(),
            s.getLifetimePrice(), s.getCurrency(), s.getPaymentEnabled(), s.getManualPaymentEnabled(), s.getSupportInfo(),s.getMockDurationMinutes());
    }
}
