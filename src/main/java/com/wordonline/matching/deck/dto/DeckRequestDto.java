package com.wordonline.matching.deck.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeckRequestDto(
        @NotBlank(message = "Deck name must not be blank")
        @Size(max = 31, message = "Deck name must be at most 31 characters")
        String name,
        @Size(max = 15, min = 15, message = "Num of cards must be 15")
        List<Long> cardIds
) {

}
