package com.wordonline.matching.magic.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Table(name = "magic_cards")
@NoArgsConstructor
@AllArgsConstructor
public class MagicCard {

    @Id
    private Long id;

    @Column("magic_id")
    private Long magicId;

    @Column("card_id")
    private Long cardId;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
