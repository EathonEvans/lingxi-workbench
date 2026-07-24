package com.lingxi.workbench.admin.Intention.model.entity;

import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IntentTreeEntity extends TimeEntity {

    private Long id;

}
