package org.example.dto.response;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class UserDto {
    private GeneralitaDto name;
    private String birthday;
    private String occupation;
    private List<ContattoDto> phones;
    private boolean favorite;
    private List<GruppoDto> groups;
    private List<String> followers;
    private Map<String, Object> additionalProperties;

    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        return additionalProperties;
    }
}
