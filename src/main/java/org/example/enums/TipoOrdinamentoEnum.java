package org.example.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TipoOrdinamentoEnum {
    ASCENDANT("asc"),
    DESCENDANT("desc");

    private final String code;
}
