package com.wordonline.matching.data.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Parameter {
    private String gameObjectName;
    private String paramName;
    private Double value;
}