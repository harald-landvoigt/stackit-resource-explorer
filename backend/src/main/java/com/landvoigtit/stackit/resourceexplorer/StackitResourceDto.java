package com.landvoigtit.stackit.resourceexplorer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class StackitResourceDto {

    @NotBlank(message = "Resource ID must not be null or empty")
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", message = "Resource ID must be a valid UUID")
    private String id;

    @NotBlank(message = "Stackit cloud Resource ID must not be null or empty")
    private String resourceId;

    @NotBlank(message = "Resource name must not be null or empty")
    @Size(max = 255, message = "Resource name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Resource type must not be null or empty")
    private String type;

    @NotBlank(message = "Resource status must not be null or empty")
    private String status;

    @NotBlank(message = "Resource region must not be null or empty")
    private String region;

    @NotBlank(message = "Resource project ID must not be null or empty")
    private String projectId;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
    private Map<String, String> tags;
    private Map<String, Object> data;
}
