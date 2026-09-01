// model/User.java
package org.lazycent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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

    @NotBlank(message = "Ім'я користувача не може бути порожнім")
    @Size(min = 6, max = 30, message = "Ім'я користувача має містити від 6 до 30 символів")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Дозволені лише латинські літери, цифри та підкреслення")
    @Column(nullable = false, unique = true)
    private String username;

    @NotBlank(message = "Пароль не може бути порожнім")
    @Size(min = 6, max = 100, message = "Пароль має містити від 6 до 100 символів")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{6,}$",
            message = "Пароль має містити малу та велику літеру, цифру та спецсимвол"
    )
    @Column(nullable = false)
    private String password;

    @NotBlank(message = "Роль не може бути порожньою")
    @Column(nullable = false)
    private String role;

    @Email(message = "Некоректна електронна адреса")
    @Column(unique = true)
    private String email;

    private String provider;
    private String providerId;

    @NotBlank(message = "Валюта не може бути порожньою")
    @Column(nullable = false)
    private String preferredCurrency = "UAH";

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<DayBudget> dayBudgets;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
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