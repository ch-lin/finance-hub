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
package ch.lin.finance.hub.backend.api.domain.model.accounting.expense;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Getter;

/**
 * A read-only entity mapping a unified view of all expense items (Amazon,
 * Rakuten, Sofmap) for the unified /expenses endpoint.
 */
@Entity
@Subselect("""
            SELECT CONCAT('AMAZON_', i.id) as id, 'AMAZON' as source, i.name as name, i.unit_price as unit_price, i.quantity as quantity,
                   c.name as category_name, c.is_essential as is_essential, o.order_date as order_date, o.owner_user_id as owner_user_id, o.privacy_level as privacy_level
            FROM amazon_item i
            JOIN amazon_order o ON i.order_id = o.id
            LEFT JOIN transaction_category c ON i.category_id = c.id
            UNION ALL
            SELECT CONCAT('RAKUTEN_', i.id) as id, 'RAKUTEN' as source, i.name as name, i.unit_price as unit_price, i.quantity as quantity,
                   c.name as category_name, c.is_essential as is_essential, o.order_date as order_date, o.owner_user_id as owner_user_id, o.privacy_level as privacy_level
            FROM rakuten_item i
            JOIN rakuten_order o ON i.order_id = o.id
            LEFT JOIN transaction_category c ON i.category_id = c.id
            UNION ALL
            SELECT CONCAT('SOFMAP_', i.id) as id, 'SOFMAP' as source, i.name as name, i.unit_price as unit_price, i.quantity as quantity,
                   c.name as category_name, c.is_essential as is_essential, o.order_date as order_date, o.owner_user_id as owner_user_id, o.privacy_level as privacy_level
            FROM sofmap_item i
            JOIN sofmap_order o ON i.order_id = o.id
            LEFT JOIN transaction_category c ON i.category_id = c.id
        """)
@Synchronize({"amazon_item", "amazon_order", "rakuten_item", "rakuten_order", "sofmap_item", "sofmap_order",
    "transaction_category"})
@Filter(name = "personalFilter", condition = "owner_user_id = :userId")
@Filter(name = "familyFilter", condition = "owner_user_id in (:familyUserIds) and (owner_user_id = :currentUserId or privacy_level != 'PRIVATE')")
@Getter
public class ExpenseItem {

    /**
     * The unique identifier constructed by combining source and original item
     * ID.
     */
    @Id
    private String id;

    @Column(name = "owner_user_id")
    private String ownerUserId;

    /**
     * The source of the expense item (e.g., AMAZON, RAKUTEN, SOFMAP).
     */
    private String source;

    /**
     * The name or description of the item.
     */
    private String name;

    /**
     * The price per unit of the item.
     */
    private BigDecimal unitPrice;

    /**
     * The number of units purchased.
     */
    private Integer quantity;

    /**
     * The name of the category assigned to this item.
     */
    private String categoryName;

    /**
     * Indicates if this item belongs to a category marked as essential.
     */
    private Boolean isEssential;

    /**
     * The date the order was placed.
     */
    private LocalDate orderDate;

    /**
     * The privacy level inherited from the order.
     */
    @Column(name = "privacy_level")
    @Enumerated(EnumType.STRING)
    private PrivacyLevel privacyLevel;

}
