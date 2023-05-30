package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.example.dto.elaborazione.VariazioneDto;
import org.example.dto.response.ContattoDto;
import org.example.dto.response.GeneralitaDto;
import org.example.dto.response.GruppoDto;
import org.example.dto.response.MateriaDto;
import org.example.dto.response.UserDto;
import org.example.dto.response.VotoDto;
import org.example.utils.JsonPathGeneratorUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Main {

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        UserDto user1 = UserDto.builder()
                .name(GeneralitaDto.builder().first("John").last("Doe").build())
                .birthday("1980-01-01")
                .occupation("Software engineer")
                .phones(List.of(
                        ContattoDto.builder()
                                .id(UUID.randomUUID().toString())
                                .number("000000000")
                                .type("home")
                                .build(),
                        ContattoDto.builder()
                                .id(UUID.randomUUID().toString())
                                .number("999999999")
                                .type("mobile")
                                .build()
                ))
                .additionalProperties(Map.of("company", "Acme"))
                .build();

        List<MateriaDto> materieList = List.of(
                new MateriaDto(
                        UUID.randomUUID().toString(), "Informatica",
                        List.of(new VotoDto(UUID.randomUUID().toString(), LocalDateTime.now(), 10),
                                new VotoDto(UUID.randomUUID().toString(), LocalDateTime.now().minusDays(5), 8.5),
                                new VotoDto(UUID.randomUUID().toString(), LocalDateTime.now(), 8.5))
                ),
                new MateriaDto(UUID.randomUUID().toString(), "Italiano", Collections.emptyList())
        );
        UserDto user2 = UserDto.builder()
                .name(GeneralitaDto.builder().first("Jane").last("Doe").nickname("Jenny").build())
                .birthday("1990-01-01")
                .phones(
                        Collections.singletonList(
                                ContattoDto.builder()
                                        .id(UUID.randomUUID().toString())
                                        .number("111111111")
                                        .type("mobile")
                                        .build()
                        )
                )
                .favorite(true)
                .groups(
                        List.of(
                                GruppoDto.builder().id(UUID.randomUUID().toString()).gruppo("close-friends").build(),
                                GruppoDto.builder().id(UUID.randomUUID().toString()).gruppo("gym").build()
                        )
                )
                .followers(List.of("Mario", "Anna", "Marco"))
                .additionalProperties(Map.of("materie", materieList))
                .build();

        Map<String, Object> leftMap = JsonPathGeneratorUtils.generateJsonPath(user1);
        Map<String, Object> rightMap = JsonPathGeneratorUtils.generateJsonPath(user2);
        List<VariazioneDto> variazioni = JsonPathGeneratorUtils.generateListaVariazioni(leftMap, rightMap);
        ObjectMapper objectMapper = new ObjectMapper();
        System.out.println("---- VARIATIONS ----\n" + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(variazioni));

        System.out.println("\n---- New Json Object ----");
        Map<String, Object> originalJson = objectMapper.convertValue(user1, Map.class);
        Map<String, Object> editedJson = JsonPathGeneratorUtils.updateJsonFromJsonPath(originalJson, variazioni);
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(editedJson));
    }
}