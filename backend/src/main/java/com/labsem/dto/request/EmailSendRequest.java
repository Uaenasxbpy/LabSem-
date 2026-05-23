package com.labsem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EmailSendRequest {
    @NotEmpty
    private List<Long> memberIds;
    private List<Long> fileIds = new ArrayList<>();
    @NotBlank
    private String subject;
    private String body;
}
