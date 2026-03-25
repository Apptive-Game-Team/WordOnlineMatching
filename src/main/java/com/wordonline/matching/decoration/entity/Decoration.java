package com.wordonline.matching.decoration.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "decorations")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Deprecated
public class Decoration {

    @Id
    private Long id;
    private String name;
    private DecoType decoType;
}
