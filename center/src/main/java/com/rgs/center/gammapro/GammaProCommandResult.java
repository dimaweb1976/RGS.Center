package com.rgs.center.gammapro;

import java.util.List;

/** Результат полностью завершённой команды Gamma-PRO. */
public record GammaProCommandResult(
        String command,
        List<String> dataLines,
        String terminalLine
) {
    public GammaProCommandResult {
        dataLines = List.copyOf(dataLines);
    }
}
