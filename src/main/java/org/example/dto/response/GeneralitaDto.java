package org.example.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GeneralitaDto {
    private String first;
    private String last;
    private String nickname;
}
