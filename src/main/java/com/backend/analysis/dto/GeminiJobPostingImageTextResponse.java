package com.backend.analysis.dto;

public record GeminiJobPostingImageTextResponse(
        String image_text
) {

    public String imageText() {
        return image_text;
    }
}
