package org.apache.ozhera.mind.api.dto;

import lombok.Data;

@Data
public class UserModelConfigDTO {
    private Long id;
    private String apiKey;
    private String modelType;
    private String modelPlatform;
}
