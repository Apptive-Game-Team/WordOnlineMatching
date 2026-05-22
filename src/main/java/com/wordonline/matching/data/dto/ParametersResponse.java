package com.wordonline.matching.data.dto;

import com.wordonline.matching.data.domain.Parameter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ParametersResponse {
    private List<Parameter> parameters;
    private String version;
    private boolean changed;

    public ParametersResponse(List<Parameter> parameters) {
        this.parameters = parameters;
        this.version = null; // Will be set in the service
        this.changed = false;
    }
}
