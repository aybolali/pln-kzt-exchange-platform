package pl.aybolali.plnkztexchangebot.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.service.UserService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String telegramUsername) throws UsernameNotFoundException {
        log.debug("Authenticating user: {}", telegramUsername);

        User user = userService.findByTelegramUsername(telegramUsername)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", telegramUsername);
                    return new UsernameNotFoundException("User not found: " + telegramUsername);
                });

        if (!user.getIsEnabled()) {
            log.warn("User disabled: {}", telegramUsername);
            throw new UsernameNotFoundException("User disabled");
        }

        log.debug("User authenticated: {}", telegramUsername);
        return new UserPrincipal(user);
    }

    // ✅ УПРОЩЕНО: меньше кода
    public record UserPrincipal(User user) implements UserDetails {

        @Override
        public List<SimpleGrantedAuthority> getAuthorities() {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        @Override
        public String getPassword() {
            return user.getTelegramUsername(); // MVP: username = password
        }

        @Override
        public String getUsername() {
            return user.getTelegramUsername();
        }

        @Override
        public boolean isEnabled() {
            return user.getIsEnabled();
        }

        @Override
        public boolean isAccountNonExpired() { return true; }

        @Override
        public boolean isAccountNonLocked() { return true; }

        @Override
        public boolean isCredentialsNonExpired() { return true; }
    }
}