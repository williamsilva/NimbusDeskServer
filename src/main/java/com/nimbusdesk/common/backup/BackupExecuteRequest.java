package com.nimbusdesk.common.backup;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BackupExecuteRequest(
    @NotNull
    @NotEmpty
    List<BackupTarget> targets) {
}
