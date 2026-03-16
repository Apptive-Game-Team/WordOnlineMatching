package com.wordonline.matching.magic.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table(name = "magics")
@NoArgsConstructor
@AllArgsConstructor
public class Magic {

    @Id
    private Long id;
    private String name;
}
