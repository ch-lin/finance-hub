/*
 * Copyright (c) 2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin. Unauthorized copying,
 * modification, distribution, reverse engineering, or use of the Software,
 * via any medium, is strictly prohibited without prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ch.lin.finance.hub.backend.api.domain.model.accounting.category;

import org.hibernate.annotations.ColumnDefault;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a classification category for financial transactions or shopping
 * items.
 * Can be hierarchical (e.g. Food -> Groceries) using the parent relation.
 */
@Entity
@Table(name = TransactionCategory.TABLE_NAME, indexes = {
        @Index(name = TransactionCategory.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN),
        @Index(name = TransactionCategory.PARENT_ID_INDEX, columnList = TransactionCategory.PARENT_ID_COLUMN)
})
@Getter
// Hibernate requires a no-argument constructor for proxying and reflective
// instantiation.
// However, from a Domain-Driven Design (DDD) perspective, an entity should
// never be created
// in an invalid state without its required fields. Therefore, we use
// AccessLevel.PROTECTED
// to satisfy JPA/Hibernate requirements while preventing arbitrary
// instantiation in business logic.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionCategory extends MultiUserBaseEntity {

    /**
     * The name of the table in the database.
     */
    public static final String TABLE_NAME = "transaction_category";

    public static final String NAME_COLUMN = "name";
    public static final String IS_ESSENTIAL_COLUMN = "is_essential";
    public static final String IS_SIMULATION_DUPLICATE_COLUMN = "is_simulation_duplicate";
    public static final String PARENT_ID_COLUMN = "parent_id";

    public static final String ID_INDEX = "transaction_category_id_index";
    public static final String PARENT_ID_INDEX = "transaction_category_parent_id_index";

    /**
     * The name of the category.
     */
    @NotNull
    @Column(name = NAME_COLUMN, nullable = false)
    @Setter
    private String name;

    /**
     * Indicates if this category is considered essential for minimum living
     * expenses.
     */
    @ColumnDefault("true")
    @Column(name = IS_ESSENTIAL_COLUMN, nullable = false)
    @Setter
    private boolean isEssential = true;

    /**
     * Indicates if this category is handled by a simulation engine and thus should
     * be ignored by historical baseline calculations to prevent double counting.
     */
    @ColumnDefault("false")
    @Column(name = IS_SIMULATION_DUPLICATE_COLUMN, nullable = false)
    @Setter
    private boolean isSimulationDuplicate = false;

    /**
     * The parent category for hierarchical classification. Null if it is a
     * top-level category.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = PARENT_ID_COLUMN, foreignKey = @ForeignKey(name = "fk_category_parent"))
    @Setter
    private TransactionCategory parent;

    /**
     * Creates a new TransactionCategory.
     * 
     * @param name                  The name of the category
     * @param isEssential           Whether this category is essential
     * @param isSimulationDuplicate Whether this category is a simulation duplicate
     * @param parent                The parent category (nullable)
     */
    public TransactionCategory(String name, boolean isEssential, boolean isSimulationDuplicate,
            TransactionCategory parent) {
        this.name = name;
        this.isEssential = isEssential;
        this.isSimulationDuplicate = isSimulationDuplicate;
        this.parent = parent;
    }
}
