package com.lingxi.workbench.chat.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.bucket.CompareAndDeleteArgs;
import org.redisson.api.bucket.CompareAndSetArgs;
import org.redisson.api.listener.MessageListener;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(activeBucket.setIfAbsent(anyString(), eq(Duration.ofMinutes(2)))).thenReturn(true);
        AtomicInteger invoked = new AtomicInteger();

        GenerationTaskRegistry registry = new GenerationTaskRegistry(client);
        GenerationTaskRegistry.RegistrationResult result = registry.register(
                12L, invoked::incrementAndGet, () -> { });

        ArgumentCaptor<MessageListener<String>> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);
        verify(topic).addListener(eq(String.class), listenerCaptor.capture());
        listenerCaptor.getValue().onMessage("lingxi:runtime:generation:cancel", "12");

        assertThat(invoked).hasValue(1);
        assertThat(result).isEqualTo(GenerationTaskRegistry.RegistrationResult.ACQUIRED);
        verify(activeBucket).setIfAbsent(anyString(), eq(Duration.ofMinutes(2)));
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
        when(activeBucket.setIfAbsent(anyString(), eq(Duration.ofMinutes(2)))).thenReturn(true);
        when(cancellationBucket.get()).thenReturn("CANCELLED");
        AtomicInteger invoked = new AtomicInteger();

        new GenerationTaskRegistry(client).register(12L, invoked::incrementAndGet, () -> { });

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
        when(activeBucket.get()).thenReturn("instance-owner-token");

        assertThat(new GenerationTaskRegistry(client).isActive(12L)).isTrue();
    }

    @Test
    void shouldRejectRegistrationWhenAnotherInstanceOwnsTask() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        RBucket<String> activeBucket = mock(RBucket.class);
        when(client.getTopic("lingxi:runtime:generation:cancel")).thenReturn(topic);
        when(topic.addListener(eq(String.class), any(MessageListener.class))).thenReturn(7);
        when(client.<String>getBucket("lingxi:runtime:generation:active:12")).thenReturn(activeBucket);
        when(activeBucket.setIfAbsent(anyString(), eq(Duration.ofMinutes(2)))).thenReturn(false);

        GenerationTaskRegistry.RegistrationResult result = new GenerationTaskRegistry(client)
                .register(12L, () -> { }, () -> { });

        assertThat(result).isEqualTo(GenerationTaskRegistry.RegistrationResult.ACTIVE_ELSEWHERE);
    }

    @Test
    void shouldReleaseLocalRegistrationWhenRedisClaimFails() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        RBucket<String> cancellationBucket = mock(RBucket.class);
        RBucket<String> activeBucket = mock(RBucket.class);
        when(client.getTopic("lingxi:runtime:generation:cancel")).thenReturn(topic);
        when(topic.addListener(eq(String.class), any(MessageListener.class))).thenReturn(7);
        when(client.<String>getBucket("lingxi:runtime:generation:cancel:12")).thenReturn(cancellationBucket);
        when(client.<String>getBucket("lingxi:runtime:generation:active:12")).thenReturn(activeBucket);
        when(activeBucket.setIfAbsent(anyString(), eq(Duration.ofMinutes(2))))
                .thenThrow(new IllegalStateException("redis unavailable"))
                .thenReturn(true);
        GenerationTaskRegistry registry = new GenerationTaskRegistry(client);

        assertThatThrownBy(() -> registry.register(12L, () -> { }, () -> { }))
                .isInstanceOf(IllegalStateException.class);
        GenerationTaskRegistry.RegistrationResult retry = registry.register(12L, () -> { }, () -> { });

        assertThat(retry).isEqualTo(GenerationTaskRegistry.RegistrationResult.ACQUIRED);
    }

    @Test
    void shouldStopLocalTaskWhenLeaseBelongsToAnotherInstance() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        RBucket<String> cancellationBucket = mock(RBucket.class);
        RBucket<String> activeBucket = mock(RBucket.class);
        when(client.getTopic("lingxi:runtime:generation:cancel")).thenReturn(topic);
        when(topic.addListener(eq(String.class), any(MessageListener.class))).thenReturn(7);
        when(client.<String>getBucket("lingxi:runtime:generation:cancel:12")).thenReturn(cancellationBucket);
        when(client.<String>getBucket("lingxi:runtime:generation:active:12")).thenReturn(activeBucket);
        when(activeBucket.setIfAbsent(anyString(), eq(Duration.ofMinutes(2))))
                .thenReturn(true, false);
        when(activeBucket.compareAndSet(any(CompareAndSetArgs.class))).thenReturn(false);
        AtomicInteger ownershipLost = new AtomicInteger();
        GenerationTaskRegistry registry = new GenerationTaskRegistry(client);
        registry.register(12L, () -> { }, ownershipLost::incrementAndGet);

        registry.refreshActiveTasks();

        assertThat(ownershipLost).hasValue(1);
    }

    @Test
    void shouldDeleteOnlyLeaseOwnedByCurrentRegistration() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        RBucket<String> cancellationBucket = mock(RBucket.class);
        RBucket<String> activeBucket = mock(RBucket.class);
        when(client.getTopic("lingxi:runtime:generation:cancel")).thenReturn(topic);
        when(topic.addListener(eq(String.class), any(MessageListener.class))).thenReturn(7);
        when(client.<String>getBucket("lingxi:runtime:generation:cancel:12")).thenReturn(cancellationBucket);
        when(client.<String>getBucket("lingxi:runtime:generation:active:12")).thenReturn(activeBucket);
        when(activeBucket.setIfAbsent(anyString(), eq(Duration.ofMinutes(2)))).thenReturn(true);
        GenerationTaskRegistry registry = new GenerationTaskRegistry(client);
        registry.register(12L, () -> { }, () -> { });

        registry.unregister(12L);

        verify(activeBucket).compareAndDelete(any(CompareAndDeleteArgs.class));
    }
}
