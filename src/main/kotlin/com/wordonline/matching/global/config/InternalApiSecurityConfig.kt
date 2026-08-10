package com.wordonline.matching.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint
import org.springframework.security.web.server.authorization.HttpStatusServerAccessDeniedHandler
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers
import reactor.core.publisher.Mono

/**
 * Authenticates the server-to-server surface under `/api/internal`.
 *
 * The lobby had no inbound service path before this. The obvious shortcut - let the existing
 * chain authenticate the call like any other - would mean any logged-in player could end
 * anyone's game session with their own token, so this needs a caller the token itself proves
 * is not a user.
 *
 * It already does. The account server mints two shapes of JWT with the same key:
 *
 * - user tokens carry `memberId` and the member's authorities;
 * - server tokens carry `type: "server_token"`, subject `server`, and no `memberId`.
 *
 * Only a `SUPER_ADMIN` can mint the second shape, and nothing a user can obtain carries that
 * claim, so `type` is a discriminator the token contents actually support rather than a
 * convention that holds until someone renames a scope. A shared secret was the alternative
 * and would have added a credential to distribute, rotate and leak, to prove something the
 * existing signature already proves.
 *
 * Callers therefore need no new kind of credential: the game server sends the same
 * pre-issued bearer token this service sends outbound
 * ([com.wordonline.matching.global.config.webclient.JwtWebClientConfig]).
 *
 * This chain is ordered ahead of
 * [com.wordonline.matching.global.config.WebSecurityConfig]'s so an `/api/internal` request
 * never reaches the user chain - which would authenticate a player's token and, on failure,
 * redirect a machine caller to `/login`.
 */
@Configuration
class InternalApiSecurityConfig(
    private val properties: InternalApiProperties,
) {
    companion object {
        const val INTERNAL_API_PATTERN = "/api/internal/**"

        /** Granted only to a verified service token; the sole authority that opens this chain. */
        const val SERVICE_AUTHORITY = "ROLE_INTERNAL_SERVICE"
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    fun internalApiSecurityFilterChain(
        http: ServerHttpSecurity,
        jwtDecoder: ReactiveJwtDecoder,
    ): SecurityWebFilterChain = http
        .securityMatcher(ServerWebExchangeMatchers.pathMatchers(INTERNAL_API_PATTERN))
        .authorizeExchange { exchanges -> exchanges.anyExchange().hasAuthority(SERVICE_AUTHORITY) }
        .oauth2ResourceServer { oauth2 ->
            oauth2.jwt { jwt ->
                jwt.jwtDecoder(jwtDecoder)
                jwt.jwtAuthenticationConverter(ServiceTokenAuthenticationConverter(properties.requiredAuthority))
            }
        }
        // A machine caller needs a status code, not the user chain's redirect to /login.
        .exceptionHandling { exceptions ->
            exceptions.authenticationEntryPoint(HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
            exceptions.accessDeniedHandler(HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN))
        }
        .csrf { it.disable() }
        .formLogin { it.disable() }
        .httpBasic { it.disable() }
        .logout { it.disable() }
        .build()
}

/**
 * Turns a verified JWT into an authentication that opens the internal API only when the token
 * is a service token.
 *
 * A user's token still authenticates - it is a valid signature - but comes out with no
 * authorities at all, so authorization denies it. That split is deliberate: the failure is a
 * `403` naming an authorization decision, not a `401` that would invite the client to retry
 * with a fresh login.
 */
class ServiceTokenAuthenticationConverter(
    private val requiredAuthority: String,
) : Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    override fun convert(source: Jwt): Mono<AbstractAuthenticationToken> =
        Mono.just(JwtAuthenticationToken(source, authorities(source)))

    private fun authorities(jwt: Jwt): Collection<GrantedAuthority> =
        if (isServiceToken(jwt)) {
            listOf(SimpleGrantedAuthority(InternalApiSecurityConfig.SERVICE_AUTHORITY))
        } else {
            emptyList()
        }

    /**
     * Three conditions, each of which a user's token fails on its own:
     *
     * - `type` is `server_token`, which only the account server's server-token endpoint sets;
     * - `memberId` is absent, because a token issued for a member always carries it;
     * - the configured authority is present, when one is configured.
     */
    private fun isServiceToken(jwt: Jwt): Boolean {
        if (jwt.getClaimAsString(TYPE_CLAIM) != SERVER_TOKEN_TYPE) return false
        if (jwt.hasClaim(MEMBER_ID_CLAIM)) return false
        return requiredAuthority.isEmpty() || requiredAuthority in scopes(jwt)
    }

    private fun scopes(jwt: Jwt): Set<String> =
        jwt.getClaimAsString(SCOPE_CLAIM).orEmpty().split(" ").filter { it.isNotBlank() }.toSet()

    private companion object {
        const val TYPE_CLAIM = "type"
        const val MEMBER_ID_CLAIM = "memberId"
        const val SCOPE_CLAIM = "scope"
        const val SERVER_TOKEN_TYPE = "server_token"
    }
}
