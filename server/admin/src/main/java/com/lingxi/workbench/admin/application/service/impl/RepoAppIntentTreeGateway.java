package com.lingxi.workbench.admin.application.service.impl;

import com.lingxi.workbench.admin.application.service.AppIntentTreeGateway;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.intent.model.entity.IntentTreeEntity;
import com.lingxi.workbench.admin.intent.model.entity.IntentTreeVersionEntity;
import com.lingxi.workbench.admin.intent.repo.IntentTreeRepo;
import com.lingxi.workbench.admin.intent.repo.IntentTreeVersionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/** 使用意图树仓库解析 Application 草稿中的逻辑引用。 */
@Component
@RequiredArgsConstructor
public class RepoAppIntentTreeGateway
        implements AppIntentTreeGateway {

    private final IntentTreeRepo intentTreeRepo;
    private final IntentTreeVersionRepo versionRepo;

    @Override
    public Optional<IntentTreeReference> findPublishedTree(Long intentTreeId) {
        Optional<IntentTreeEntity> treeOptional = intentTreeRepo.findById(intentTreeId);
        if (treeOptional.isEmpty()) {
            return Optional.empty();
        }
        IntentTreeEntity tree = treeOptional.get();
        if (Objects.isNull(tree.getPublishedVersionId())) {
            return Optional.of(new IntentTreeReference(
                    tree.getId(), tree.getStatus() == EnableStatus.ACTIVE, null, null));
        }
        IntentTreeVersionEntity version = versionRepo
                .findByIntentTreeIdAndId(tree.getId(), tree.getPublishedVersionId())
                .orElse(null);
        return Optional.of(new IntentTreeReference(
                tree.getId(),
                tree.getStatus() == EnableStatus.ACTIVE,
                Objects.isNull(version) ? null : version.getId(),
                Objects.isNull(version) ? null : version.getDefinitionSnapshot()));
    }

    @Override
    public Optional<Long> findDomainId(Long intentTreeId) {
        return intentTreeRepo.findById(intentTreeId).map(IntentTreeEntity::getDomainId);
    }
}
