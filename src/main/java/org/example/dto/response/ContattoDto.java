package org.example.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ContattoDto {
    private String id;
    private String number;
    private String type;
}
