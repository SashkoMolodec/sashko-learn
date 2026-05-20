package com.sashkolearn.api.telegram.dto;

import java.util.Map;

public record BotResponse(
    String text,
    Map<String, String> buttons
) {
    public static BotResponse text(String text) {
        return new BotResponse(text, null);
    }

    public static BotResponse withButtons(String text, Map<String, String> buttons) {
        return new BotResponse(text, buttons);
    }
}
