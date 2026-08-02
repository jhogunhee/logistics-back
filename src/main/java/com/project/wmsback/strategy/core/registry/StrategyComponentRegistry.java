package com.project.wmsback.strategy.core.registry;

import com.project.wmsback.strategy.core.descriptor.ComponentDescriptor;
import com.project.wmsback.strategy.core.descriptor.StrategyComponent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 전략 구성요소 레지스트리. Spring이 주입한 구현체 전부를 "종류 인터페이스"
 * (InspectionRule, PutawayMethod …)별 × code별로 인덱싱한다.
 * 화면 선택지(descriptors)와 실행 시 조회(get)가 전부 여기서 나온다 —
 * 코드에 없는 옵션은 화면에 존재할 수 없다 (P1).
 */
@Component
public class StrategyComponentRegistry {

    private final Map<Class<?>, Map<String, StrategyComponent>> byType = new HashMap<>();

    public StrategyComponentRegistry(List<StrategyComponent> components) {
        for (StrategyComponent component : components) {
            for (Class<?> iface : component.getClass().getInterfaces()) {
                if (StrategyComponent.class.isAssignableFrom(iface) && iface != StrategyComponent.class) {
                    StrategyComponent prev = byType
                            .computeIfAbsent(iface, k -> new LinkedHashMap<>())
                            .put(component.descriptor().code(), component);
                    if (prev != null) {
                        throw new IllegalStateException("전략 구성요소 code 충돌: "
                                + iface.getSimpleName() + "/" + component.descriptor().code());
                    }
                }
            }
        }
    }

    /**
     * 실행용 조회. 없으면 예외 — "저장된 정의가 배포본과 어긋남"을 뜻하며,
     * 저장 시 검증(P2) 때문에 정상 경로에서는 나올 수 없다 (운영 알람 대상).
     */
    public <T extends StrategyComponent> T get(Class<T> type, String code) {
        StrategyComponent component = byType.getOrDefault(type, Map.of()).get(code);
        if (component == null) {
            throw new IllegalStateException("저장된 전략 정의가 배포본과 어긋납니다 — 미등록 구성요소: "
                    + type.getSimpleName() + "/" + code);
        }
        return type.cast(component);
    }

    /** 저장 시 검증용 조회 — 없으면 empty를 돌려주고 저장 서비스가 거부 메시지를 만든다 */
    public <T extends StrategyComponent> Optional<T> find(Class<T> type, String code) {
        return Optional.ofNullable(byType.getOrDefault(type, Map.of()).get(code)).map(type::cast);
    }

    /** 화면 선택지. code 순 정렬 — 표시 순서가 배포마다 흔들리지 않게 */
    public <T extends StrategyComponent> List<ComponentDescriptor> descriptors(Class<T> type) {
        List<ComponentDescriptor> descriptors = new ArrayList<>(
                byType.getOrDefault(type, Map.of()).values().stream()
                        .map(StrategyComponent::descriptor)
                        .toList());
        descriptors.sort(Comparator.comparing(ComponentDescriptor::code));
        return descriptors;
    }
}
