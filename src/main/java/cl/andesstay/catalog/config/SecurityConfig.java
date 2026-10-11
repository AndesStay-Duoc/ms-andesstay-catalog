package cl.andesstay.catalog.config;

import cl.andesstay.catalog.security.AzureRolesConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                    .requestMatchers("/actuator/health").permitAll()
                    // Los endpoints internos YA NO son anónimos.
                    //
                    // Antes /api/catalog/internal/** estaba en permitAll con el argumento de que
                    // solo se alcanzaba dentro de la red del compose. El argumento no se sostiene:
                    // cualquiera que llegue al puerto 8082 podía mover los cupos de cualquier
                    // unidad sin token, y el denyAll del BFF solo protege a quien pasa por el BFF.
                    // Ahora exigen JWT válido, y el rol lo comprueba cada método con @PreAuthorize.
                    .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtConverter())));
        return http.build();
    }

    /**
     * Convierte el claim "roles" en authorities y, para el huésped autoregistrado sin App Role,
     * deriva ROLE_Cliente.
     *
     * Antes este servicio usaba un JwtGrantedAuthoritiesConverter simple, que no hacía esa
     * derivación: un Cliente quedaba autenticado pero sin ninguna authority, de modo que leer el
     * catálogo le devolvía 403. Solo no se notaba porque el BFF ni siquiera le dejaba llegar.
     */
    @Bean
    public JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(new AzureRolesConverter());
        return c;
    }
}
