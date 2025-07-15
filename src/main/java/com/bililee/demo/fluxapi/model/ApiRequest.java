package com.bililee.demo.fluxapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import java.util.List;

@Data
public class ApiRequest {

    @JsonProperty("indicator_id")
    private List<String> indicatorIds;

    @JsonProperty("timestamp")
    private String timestamp;
    
}
