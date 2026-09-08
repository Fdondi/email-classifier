package com.emailtool.api.dto;

import java.util.List;

public record SystemStatusDto(
        String buildVersion,
        String mailSource,
        boolean imapCredentialsPresent,
        String imapHost,
        String llmBaseUrl,
        String llmModel,
        boolean llmReachable,
        List<String> llmModels,
        String llmError) {}
