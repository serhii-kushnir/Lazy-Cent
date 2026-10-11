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

    @Column(nullable = false)
    private String title;

    @Column(length = 5000)
    private String content;

    private String color;

    @Column(nullable = false, columnDefinition = "VARCHAR(255) DEFAULT '#212529'")
    private String textColor = "#212529";

    private boolean pinned = false;

    private boolean archived = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * P2.c: {@code owner} тепер LAZY.
     *
     * <p><b>Де читається:</b>
     * <ul>
     *   <li>{@code NoteService.getOwnedNote} — використовує {@code findByIdAndOwner},
     *       де owner у WHERE-фільтрі; явного {@code .getOwner()} немає;</li>
     *   <li>Thymeleaf {@code notes.html} — {@code note.owner} НЕ використовується.</li>
     * </ul>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;
}