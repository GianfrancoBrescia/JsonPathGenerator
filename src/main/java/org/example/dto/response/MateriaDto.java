package org.example.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class MateriaDto {
    private String id;
    private String nome;
    private List<VotoDto> voti;
}
