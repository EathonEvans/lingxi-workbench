package com.lingxi.workbench.chat.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class GenerationTaskRegistryTest {

    @Test
    void shouldDispatchRemoteCancellationToRegisteredLocalTask() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        RBucket<String> cancellationBucket = mock(RBucket.class);
        RBucket<String> activeBucket = mock(RBucket.class);
        when(client.getTopic("lingxi:runtime:generation:cancel")).thenReturn(topic);
        when(topic.addListener(eq(String.class), any(MessageListener.class))).thenReturn(7);
        when(client.<String>getBucket("lingxi:runtime:generation:cancel:12")).thenReturn(cancellationBucket);
        when(client.<String>getBucket("lingxi:runtime:generation:active:12")).thenReturn(activeBucket);
        AtomicInteger invoked = new AtomicInteger();

        GenerationTaskRegistry registry = new GenerationTaskRegistry(client);
        registry.register(12L, invoked::incrementAndGet);

        ArgumentCaptor<MessageListener<String>> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);
        verify(topic).addListener(eq(String.class), listenerCaptor.capture());
        listenerCaptor.getValue().onMessage("lingxi:runtime:generation:cancel", "12");

        assertThat(invoked).hasValue(1);
        verify(activeBucket).set("ACTIVE", Duration.ofMinutes(2));
    }

    @Test
    void shouldTriggerImmediatelyWhenCancellationWasPublishedBeforeRegistration() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        RBucket<String> cancellationBucket = mock(RBucket.class);
        RBucket<String> activeBucket = mock(RBucket.class);
        when(client.getTopic("lingxi:runtime:generation:cancel")).thenReturn(topic);
        when(topic.addListener(eq(String.class), any(MessageListener.class))).thenReturn(7);
        when(client.<String>getBucket("lingxi:runtime:generation:cancel:12")).thenReturn(cancellationBucket);
        when(client.<String>getBucket("lingxi:runtime:generation:active:12")).thenReturn(activeBucket);
        when(cancellationBucket.get()).thenReturn("CANCELLED");
        AtomicInteger invoked = new AtomicInteger();

        new GenerationTaskRegistry(client).register(12L, invoked::incrementAndGet);

        assertThat(invoked).hasValue(1);
    }

    @Test
    void shouldPersistBoundedCancellationMarkerBeforeBroadcasting() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        RBucket<String> cancellationBucket = mock(RBucket.class);
        when(client.getTopic("lingxi:runtime:generation:cancel")).thenReturn(topic);
        when(client.<String>getBucket("lingxi:runtime:generation:cancel:12")).thenReturn(cancellationBucket);

        new GenerationTaskRegistry(client).requestCancellation(12L);

        org.mockito.InOrder order = inOrder(cancellationBucket, topic);
        order.verify(cancellationBucket).set("CANCELLED", Duration.ofHours(24));
        order.verify(topic).publish("12");
    }

    @Test
    void shouldReportRemoteTaskAsActiveFromBoundedLease() {
        RedissonClient client = mock(RedissonClient.class);
        RBucket<String> activeBucket = mock(RBucket.class);
        when(client.<String>getBucket("lingxi:runtime:generation:active:12")).thenReturn(activeBucket);
        when(activeBucket.get()).thenReturn("ACTIVE");

        assertThat(new GenerationTaskRegistry(client).isActive(12L)).isTrue();
    }
}
