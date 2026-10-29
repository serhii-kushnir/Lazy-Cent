package org.lazycent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    /**
     * BCrypt-хеш пароля.
     *
     * <p><b>@JsonIgnore критично:</b> якщо User колись потрапить у JSON-відповідь
     * (наприклад, новий /api/users/me або випадкова серіалізація у логах через Jackson),
     * без цієї анотації хеш витече. Це не пароль у чистому вигляді, але дозволяє
     * офлайн-брутфорс.
     *
     * <p>Аналогічно для {@code providerId} — це внутрішній ідентифікатор у провайдера,
     * і його розкриття створює поверхню для targeted атак.
     */
    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role;

    /**
     * Email nullable: OAuth2-провайдери не завжди його повертають.
     * unique = true дозволяє декілька NULL (стандартна поведінка SQL).
     */
    @Column(unique = true)
    private String email;

    private String provider;

    @JsonIgnore
    private String providerId;

    @Column(nullable = false)
    private String preferredCurrency = "UAH";

    @Column(nullable = true)
    private String preferredLanguage;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<DayBudget> dayBudgets;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;

    @Transient
    public Long getFamilyId() {
        return family == null ? null : family.getId();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}