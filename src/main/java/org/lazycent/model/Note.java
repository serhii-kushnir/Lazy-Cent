package org.lazycent.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Optimistic locking — захист від lost update.
     * Дві вкладки редагують → друга отримає ObjectOptimisticLockingFailureException
     * (обробляється в GlobalExceptionHandler → повідомлення "Дані були змінені в іншій сесії").
     */
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 5000)
    private String content;

    private String color;

    @Column(nullable = false, columnDefinition = "VARCHAR(255) DEFAULT '#212529'")
    private String textColor = "#212529";

    private boolean pinned = false;

    private boolean archived = false;

    @Column(name = "share_with_family", nullable = false)
    private Boolean shareWithFamily = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;
}