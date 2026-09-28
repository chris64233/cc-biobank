package com.chris64233.cc.biobank.consent.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSubjectRequest(
        @NotBlank(message = "受试者编号不能为空") String subjectCode) {
}
