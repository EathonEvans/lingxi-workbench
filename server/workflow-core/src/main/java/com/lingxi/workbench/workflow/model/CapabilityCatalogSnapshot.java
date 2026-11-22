package com.lingxi.workbench.workflow.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** ApplicationVersion 允许执行的不可变能力目录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CapabilityCatalogSnapshot {

    private List<CapabilityDefinition> capabilities;

    public List<CapabilityDefinition> capabilities() {
        return capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    public static CapabilityCatalogSnapshot empty() {
        return CapabilityCatalogSnapshot.builder().capabilities(List.of()).build();
    }
}
