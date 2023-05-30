package org.example.dto.elaborazione;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JsonPathItemList {
    private int index;
    private String key;
    private Object value;
}
