package com.lingxi.workbench.observability.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiObservationAttributeFilterTest {

    @Test
    void shouldOnlyPropagateWhitelistedTraceDimensions() {
        Observation.Context parentContext = new Observation.Context();
        parentContext.addHighCardinalityKeyValue(io.micrometer.common.KeyValue.of("user.id", "7"));
        parentContext.addHighCardinalityKeyValue(io.micrometer.common.KeyValue.of("secret", "must-not-propagate"));
        parentContext.addLowCardinalityKeyValue(io.micrometer.common.KeyValue.of("lingxi.application.code", "career_assistant"));
        ObservationView parent = mock(ObservationView.class);
        when(parent.getContextView()).thenReturn(parentContext);
        Observation.Context child = new Observation.Context();
        child.setParentObservation(parent);

        new AiObservationAttributeFilter().map(child);

        assertEquals("7", child.getHighCardinalityKeyValue("user.id").getValue());
        assertEquals("career_assistant", child.getLowCardinalityKeyValue("lingxi.application.code").getValue());
        assertNull(child.getHighCardinalityKeyValue("secret"));
    }
}
