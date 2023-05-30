package org.example.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.example.dto.elaborazione.JsonPathItemList;
import org.example.dto.elaborazione.VariazioneDto;
import org.example.enums.TipoOperazioneEnum;
import org.example.enums.TipoOrdinamentoEnum;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
public class JsonPathGeneratorUtils {

    private static final String CHECK_LIST_REGEX = "\\[.*";
    private static final String INDEX_ITEM_LIST_REGEX = ".*\\d+.*";
    private static final String SPLIT_REGEX = "\\.(?![^\\[\\]]*])";

    /**
     * Metodo che genera una mappa chiave - valore costruita da <jsonPath - valore>
     *
     * @param object Oggetto di partenza da cui generare i jsonPath
     * @return Mappa<jsonPath - valore>
     */
    public static Map<String, Object> generateJsonPath(Object object) {
        Map<String, Object> map = convertObjectIntoMap(object);
        return flatten(map)
                .entrySet()
                .stream()
                .flatMap(JsonPathGeneratorUtils::finalizeJsonPath)
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertObjectIntoMap(Object object) {
        Map<String, Object> map = new ObjectMapper().convertValue(object, Map.class);
        map.remove("additionalProperties");
        return map;
    }

    private Map<Object, Collection<Object>> flatten(Map<String, Object> map) {
        return map.entrySet()
                .stream()
                .flatMap(e -> buildJsonPath(e, false, -1))
                .collect(
                        LinkedHashMap::new,
                        (m, e) -> {
                            String key = "$." + e.getKey();
                            if (m.containsKey(key)) {
                                List<Object> list = (List<Object>) m.get(key);
                                list.add(e.getValue());
                                m.put(key, list);
                            } else {
                                m.put(key, new ArrayList<>(Collections.singleton(e.getValue())));
                            }
                        },
                        LinkedHashMap::putAll
                );
    }

    private Stream<Map.Entry<String, Object>> buildJsonPath(Map.Entry<String, Object> entry, boolean isItemList,
                                                            int index) {
        if (entry == null) return Stream.empty();

        String entryKey = entry.getKey();
        Object entryValue = entry.getValue();
        if (entryValue instanceof Map<?, ?>) {
            MutableObject<String> id = new MutableObject<>();
            return ((Map<?, ?>) entryValue).entrySet()
                    .stream()
                    .flatMap(e -> {
                        String path;
                        if (isItemList && index > -1) {
                            if (Objects.equals(e.getKey(), "id")) {
                                path = entryKey + "[" + index + "].id";
                                id.setValue(String.valueOf(e.getValue()));
                            } else {
                                path = entryKey + "[?(@.id == '" + id.getValue() + "')]." + e.getKey();
                            }
                        } else {
                            path = entryKey + "." + e.getKey();
                        }
                        return buildJsonPath(new AbstractMap.SimpleEntry<>(path, e.getValue()), false, index);
                    });
        } else if (entryValue instanceof List<?>) {
            List<?> list = (List<?>) entryValue;
            if (list.isEmpty()) {
                return Stream.of(new AbstractMap.SimpleEntry<>(entryKey, entryValue));
            }
            return IntStream.range(0, list.size())
                    .mapToObj(i -> JsonPathItemList.builder().index(i).key(entryKey).value(list.get(i)).build())
                    .flatMap(e -> buildJsonPath(new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()), true, e.getIndex()));
        }

        return Stream.of(entry);
    }

    private Stream<Map.Entry<String, Object>> finalizeJsonPath(Map.Entry<Object, Collection<Object>> multiValuedMap) {
        String key = String.valueOf(multiValuedMap.getKey());
        List<Object> list = new ArrayList<>(multiValuedMap.getValue());

        if (!list.isEmpty()) {
            if (list.size() > 1) {
                return IntStream.range(0, list.size())
                        .mapToObj(i -> new AbstractMap.SimpleEntry<>(key + "[" + i + "]", list.get(i)));
            }
            return Stream.of(new AbstractMap.SimpleEntry<>(key, list.get(0)));
        }
        return Stream.of(new AbstractMap.SimpleEntry<>(key, null));
    }


    /**
     * Metodo che crea la lista completa di variazioni
     *
     * @param leftMap  Mappa jsonPath-valore dell'oggetto originale
     * @param rightMap Mappa jsonPath-valore dell'oggetto modificato
     * @return Lista di tipo VariazioneDto
     */
    public static List<VariazioneDto> generateListaVariazioni(Map<String, Object> leftMap, Map<String, Object> rightMap) {
        MapDifference<String, Object> difference = Maps.difference(leftMap, rightMap);
        List<VariazioneDto> variazioni = Stream.of(difference.entriesOnlyOnRight(), difference.entriesDiffering())
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .map(e ->
                        new VariazioneDto(
                                e.getKey().substring(e.getKey().lastIndexOf(".") + 1).replaceAll(CHECK_LIST_REGEX, StringUtils.EMPTY),
                                e.getKey(),
                                e.getValue() instanceof MapDifference.ValueDifference<?> ? ((MapDifference.ValueDifference<?>) e.getValue()).rightValue() : e.getValue(),
                                LocalDateTime.now(), TipoOperazioneEnum.UPDATE.name()
                        )
                )
                .collect(Collectors.toList());
        difference.entriesOnlyOnLeft().forEach((key, value) -> {
            List<String> split1 = Arrays.asList(key.split(SPLIT_REGEX));
            String lastSegmentPath = split1.get(split1.size() - 1);
            if ((lastSegmentPath.equals("id") || lastSegmentPath.matches(INDEX_ITEM_LIST_REGEX))
                    && !variazioni.stream().map(VariazioneDto::getJsonPath).collect(Collectors.toList()).contains(key)) {
                variazioni.addAll(
                        difference.entriesOnlyOnLeft().entrySet()
                                .stream()
                                .filter(e -> {
                                    List<String> split2 = new ArrayList<>(split1.subList(0, split1.size() - 1));
                                    String join = String.join(".", split2);
                                    boolean esito = e.getKey().startsWith(join);
                                    if (!esito) {
                                        split2.set(split2.size() - 1, split2.get(split2.size() - 1).replaceAll("(.*)\\[\\d+](.*)", "$1[?(@.id == '" + value + "')]$2"));
                                        esito = e.getKey().startsWith(String.join(".", split2));
                                    }
                                    return esito;
                                })
                                .map(e ->
                                        new VariazioneDto(
                                                e.getKey().substring(e.getKey().lastIndexOf(".") + 1).replaceAll(CHECK_LIST_REGEX, StringUtils.EMPTY),
                                                e.getKey(), e.getValue(), LocalDateTime.now(),
                                                TipoOperazioneEnum.DELETE.name()
                                        )
                                ).collect(Collectors.toList())
                );
            }
        });

        return variazioni;
    }


    /**
     * Metodo che modifica un oggetto json a partire da una lista di jsonPath
     *
     * @param originalJson Oggetto di partenza da modificare
     * @param variazioni   Lista composta da jsonPath e valore variato; partendo dal json path ricorsivamente viene
     *                     modificato il nodo nel json con il nuovo valore
     * @return Oggetto json modificato
     */
    @SneakyThrows
    @SuppressWarnings({"unchecked", "java:S3776"})
    public static Map<String, Object> updateJsonFromJsonPath(Map<String, Object> originalJson, List<VariazioneDto> variazioni) {
        List<VariazioneDto> orderedVariazioniList = getOrderedVariazioniList(variazioni);

        ObjectMapper objectMapper = new ObjectMapper();
        MutableObject<String> json = new MutableObject<>(objectMapper.writeValueAsString(originalJson));
        json.setValue(JsonPath.parse(json.getValue()).delete("$.additionalProperties").jsonString());

        Configuration configuration = getConfiguration();

        orderedVariazioniList.stream()
                .filter(variazione -> TipoOperazioneEnum.DELETE.name().equals(variazione.getTipoOperazione()))
                .sorted(compareVariazioni(TipoOrdinamentoEnum.DESCENDANT.getCode()))
                .forEach(variazione -> {
                    String jsonPath = variazione.getJsonPath();
                    if (variazione.getAttributo().equals("id")) {
                        jsonPath = jsonPath.substring(0, jsonPath.lastIndexOf("."));
                    }
                    json.setValue(JsonPath.parse(json.getValue()).delete(jsonPath).jsonString());
                });

        for (VariazioneDto variazione : orderedVariazioniList.stream().filter(variazione -> TipoOperazioneEnum.UPDATE.name().equals(variazione.getTipoOperazione())).collect(Collectors.toList())) {
            String jsonPath = variazione.getJsonPath();
            Object nuovoValore = variazione.getValore();
            String[] splittedJsonPath = jsonPath.split(SPLIT_REGEX);
            String lastSegmentBeforeAttribute = splittedJsonPath[splittedJsonPath.length - 1];
            try {
                Object parsedJsonPath = JsonPath.compile(jsonPath).set(configuration.jsonProvider().parse(json.getValue()), nuovoValore, configuration);
                json.setValue(JsonPath.parse(parsedJsonPath).json().toString());
            } catch (ClassCastException | NullPointerException | PathNotFoundException e1) {
                if (lastSegmentBeforeAttribute.contains("[")) {
                    addNodeToJsonArray(json, jsonPath, configuration, false);
                    String listPath = jsonPath.substring(0, jsonPath.indexOf("["));
                    int indexItemList = Integer.parseInt(jsonPath.substring(jsonPath.indexOf("[") + 1, jsonPath.indexOf("]")));
                    List<Object> list = JsonPath.read(json.getValue(), listPath);
                    try {
                        list.set(indexItemList, nuovoValore);
                    } catch (IndexOutOfBoundsException | NullPointerException e2) {
                        if (list == null) list = new ArrayList<>();
                        list.add(nuovoValore);
                    }
                    Map<String, Object> tempMap = objectMapper.readValue(json.getValue(), Map.class);
                    tempMap.put(variazione.getAttributo(), list);
                    json.setValue(objectMapper.writeValueAsString(tempMap));
                } else {
                    JsonPath compiledPath = JsonPath.compile(jsonPath);
                    jsonPath = String.join(".", Arrays.asList(splittedJsonPath).subList(0, splittedJsonPath.length - 1));
                    if (Objects.equals(jsonPath.substring(jsonPath.lastIndexOf("]") + 1), StringUtils.EMPTY)) {
                        addNodeToJsonArray(json, jsonPath, configuration, true);
                        Object parsedJsonPath = compiledPath.set(configuration.jsonProvider().parse(json.getValue()), nuovoValore, configuration);
                        json.setValue(JsonPath.parse(parsedJsonPath).json().toString());
                    } else {
                        generateMissingObjects(json, variazione.getJsonPath(), configuration,
                                Arrays.asList(splittedJsonPath), new MutableInt(3), nuovoValore);
                    }
                }
            }
        }

        return objectMapper.readValue(json.getValue(), Map.class);
    }

    private Configuration getConfiguration() {
        return Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider())
                .mappingProvider(new JacksonMappingProvider())
                .options(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.CREATE_MISSING_PROPERTIES_ON_DEFINITE_PATH)
                .build();
    }

    private List<VariazioneDto> getOrderedVariazioniList(List<VariazioneDto> list) {
        List<VariazioneDto> orderedVariazioniList = new ArrayList<>();

        List<VariazioneDto> variazioniCampiSemplici = list.stream()
                .filter(e -> !e.getJsonPath().matches(INDEX_ITEM_LIST_REGEX))
                .collect(Collectors.toList());

        List<VariazioneDto> variazioniSezioniEliminate = variazioniCampiSemplici.stream()
                .filter(e -> e.getValore() instanceof ArrayList && ((ArrayList<?>) e.getValore()).isEmpty())
                .filter(v -> list.stream().noneMatch(e -> e.getJsonPath().startsWith(v.getJsonPath()) && v.getTimestamp().isBefore(e.getTimestamp())))
                .collect(Collectors.toList());

        list.removeAll(variazioniCampiSemplici);

        List<VariazioneDto> latestVariazioni = list.stream()
                .collect(Collectors.groupingBy(VariazioneDto::getJsonPath))
                .values()
                .stream()
                .map(variazioneEntities -> variazioneEntities.stream().max(Comparator.comparing(VariazioneDto::getTimestamp)).orElse(null))
                .filter(Objects::nonNull)
                .sorted(compareVariazioni(TipoOrdinamentoEnum.ASCENDANT.getCode()))
                .collect(Collectors.toList());

        variazioniCampiSemplici.removeAll(variazioniSezioniEliminate);

        orderedVariazioniList.addAll(variazioniCampiSemplici);
        orderedVariazioniList.addAll(latestVariazioni);
        orderedVariazioniList.addAll(variazioniSezioniEliminate);

        return orderedVariazioniList;
    }

    private Comparator<VariazioneDto> compareVariazioni(String orderType) {
        return new Comparator<VariazioneDto>() {
            public int compare(VariazioneDto v1, VariazioneDto v2) {
                return TipoOrdinamentoEnum.ASCENDANT.getCode().equals(orderType)
                        ? extractBigInteger(v1).compareTo(extractBigInteger(v2))
                        : extractBigInteger(v2).compareTo(extractBigInteger(v1));
            }

            BigInteger extractBigInteger(VariazioneDto v) {
                String num = v.getJsonPath().replaceAll("\\D", "");
                // return 0 if no digits found
                return num.isEmpty() ? BigInteger.valueOf(0) : new BigInteger(num);
            }
        };
    }

    private void addNodeToJsonArray(MutableObject<String> json, String jsonPath, Configuration configuration,
                                    boolean isObjectList) {
        jsonPath = jsonPath.substring(0, jsonPath.lastIndexOf("["));
        List<Object> objectList;
        try {
            List<Object> objectListRead = JsonPath.read(json.getValue(), jsonPath);
            if (objectListRead != null && !objectListRead.isEmpty()) {
                Object object = objectListRead.get(0);
                objectList = object instanceof JSONArray ? new ArrayList<>((JSONArray) object) : objectListRead;
            } else {
                objectList = new LinkedList<>();
            }
        } catch (PathNotFoundException pfe) {
            objectList = new LinkedList<>();
        }
        if (isObjectList) {
            objectList.add(new JSONObject());
        }
        Object parsedJsonPath = JsonPath.compile(jsonPath).set(configuration.jsonProvider().parse(json.getValue()), objectList, configuration);
        json.setValue(JsonPath.parse(parsedJsonPath).json().toString());
    }

    private void generateMissingObjects(MutableObject<String> json, String fullJsonPath, Configuration configuration,
                                        List<String> splittedJsonPath, MutableInt index, Object nuovoValore) {
        String jsonPath = String.join(".", splittedJsonPath.subList(0, index.getValue()));
        if (!Objects.equals(jsonPath, fullJsonPath)) {
            index.increment();
            Object parsedJsonPath = JsonPath.compile(jsonPath).set(configuration.jsonProvider().parse(json.getValue()), new JSONObject(), configuration);
            json.setValue(JsonPath.parse(parsedJsonPath).json().toString());
            generateMissingObjects(json, fullJsonPath, configuration, splittedJsonPath, index, nuovoValore);
        } else {
            Object parsedJsonPath = JsonPath.compile(jsonPath).set(configuration.jsonProvider().parse(json.getValue()), nuovoValore, configuration);
            json.setValue(JsonPath.parse(parsedJsonPath).json().toString());
        }
    }
}