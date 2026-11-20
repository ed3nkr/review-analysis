package kr.dongguk.backend.menu.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AliasesRequest(@NotNull List<String> aliases) {}