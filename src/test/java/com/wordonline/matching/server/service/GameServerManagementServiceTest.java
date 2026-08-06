package com.wordonline.matching.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.wordonline.matching.server.client.GameServerClient;
import com.wordonline.matching.server.entity.Server;
import com.wordonline.matching.server.entity.ServerState;
import com.wordonline.matching.server.entity.ServerType;
import com.wordonline.matching.session.repository.ServerRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GameServerManagementServiceTest {

    private static final int FAILURE_THRESHOLD = 3;
    private static final String ALPHA_URL = "http://alpha:9090";
    private static final String BETA_URL = "http://beta:9090";

    @Mock
    private ServerRepository serverRepository;
    @Mock
    private GameServerClient gameServerClient;

    private ServerHealthRegistry serverHealthRegistry;
    private GameServerManagementService gameServerManagementService;

    @BeforeEach
    void setUp() {
        serverHealthRegistry = new ServerHealthRegistry(FAILURE_THRESHOLD);
        gameServerManagementService =
                new GameServerManagementService(serverRepository, gameServerClient, serverHealthRegistry);
    }

    private static Server server(long id, String domain, ServerState state) {
        Server server = new Server();
        ReflectionTestUtils.setField(server, "id", id);
        ReflectionTestUtils.setField(server, "protocol", "http");
        ReflectionTestUtils.setField(server, "domain", domain);
        ReflectionTestUtils.setField(server, "port", 9090);
        ReflectionTestUtils.setField(server, "state", state);
        ReflectionTestUtils.setField(server, "type", ServerType.GAME);
        return server;
    }

    private void stubDiscovery(Server... servers) {
        when(serverRepository.findAllByType(ServerType.GAME)).thenReturn(Flux.just(servers));
    }

    private void refresh() {
        StepVerifier.create(gameServerManagementService.refresh()).verifyComplete();
    }

    private List<Long> availableIds() {
        return gameServerManagementService.getAvailableServers().stream().map(Server::getId).toList();
    }

    @Test
    void 한_서버의_헬스체크_실패가_다른_서버의_상태_갱신을_막지_않는다() {
        stubDiscovery(
                server(1L, "alpha", ServerState.ACTIVE),
                server(2L, "beta", ServerState.ACTIVE)
        );
        when(gameServerClient.healthcheck(ALPHA_URL))
                .thenReturn(Mono.error(new IllegalStateException("boom")));
        when(gameServerClient.healthcheck(BETA_URL)).thenReturn(Mono.just(true));

        refresh();

        assertThat(availableIds()).containsExactly(2L);
        assertThat(serverHealthRegistry.consecutiveFailures(1L)).isEqualTo(1);
    }

    @Test
    void DB가_INACTIVE로_표시한_서버도_헬스체크에_성공하면_배정_대상이_된다() {
        stubDiscovery(server(1L, "alpha", ServerState.INACTIVE));
        when(gameServerClient.healthcheck(ALPHA_URL)).thenReturn(Mono.just(true));

        refresh();

        assertThat(availableIds()).containsExactly(1L);
    }

    @Test
    void DRAINING_서버는_헬스체크에_성공해도_배정_대상에서_제외된다() {
        stubDiscovery(
                server(1L, "alpha", ServerState.DRAINING),
                server(2L, "beta", ServerState.ACTIVE)
        );
        when(gameServerClient.healthcheck(ALPHA_URL)).thenReturn(Mono.just(true));
        when(gameServerClient.healthcheck(BETA_URL)).thenReturn(Mono.just(true));

        refresh();

        assertThat(availableIds()).containsExactly(2L);
        assertThat(gameServerManagementService.getAvailableServer())
                .map(Server::getId)
                .contains(2L);
    }

    @Test
    void 연속_실패가_임계값에_도달해야_사용_불가로_판정하고_성공_한_번에_즉시_복구한다() {
        stubDiscovery(server(1L, "alpha", ServerState.ACTIVE));

        when(gameServerClient.healthcheck(ALPHA_URL)).thenReturn(Mono.just(true));
        refresh();
        assertThat(availableIds()).containsExactly(1L);

        when(gameServerClient.healthcheck(ALPHA_URL)).thenReturn(Mono.just(false));
        refresh();
        assertThat(availableIds()).as("1회 실패로는 내리지 않는다").containsExactly(1L);
        refresh();
        assertThat(availableIds()).as("2회 실패로도 내리지 않는다").containsExactly(1L);
        refresh();
        assertThat(availableIds()).as("3회 연속 실패면 내린다").isEmpty();

        when(gameServerClient.healthcheck(ALPHA_URL)).thenReturn(Mono.just(true));
        refresh();
        assertThat(availableIds()).as("성공 1회면 즉시 복구한다").containsExactly(1L);
        assertThat(serverHealthRegistry.consecutiveFailures(1L)).isZero();
    }

    @Test
    void 한_번도_성공하지_못한_서버는_배정_대상이_아니다() {
        stubDiscovery(server(1L, "alpha", ServerState.ACTIVE));
        when(gameServerClient.healthcheck(ALPHA_URL)).thenReturn(Mono.just(false));

        refresh();

        assertThat(availableIds()).isEmpty();
    }

    @Test
    void 리로드해도_헬스_이력이_유지된다() {
        // every refresh re-reads the row and hands back a brand new Server instance,
        // so the failure counter can only survive if it is keyed by server id.
        when(serverRepository.findAllByType(ServerType.GAME))
                .thenAnswer(invocation -> Flux.just(server(1L, "alpha", ServerState.ACTIVE)));

        when(gameServerClient.healthcheck(ALPHA_URL)).thenReturn(Mono.just(true));
        refresh();

        when(gameServerClient.healthcheck(ALPHA_URL)).thenReturn(Mono.just(false));
        refresh();
        assertThat(serverHealthRegistry.consecutiveFailures(1L)).isEqualTo(1);
        refresh();
        assertThat(serverHealthRegistry.consecutiveFailures(1L)).isEqualTo(2);
        assertThat(availableIds()).containsExactly(1L);
        refresh();
        assertThat(serverHealthRegistry.consecutiveFailures(1L)).isEqualTo(FAILURE_THRESHOLD);
        assertThat(availableIds()).isEmpty();
    }

    @Test
    void 사라진_서버의_헬스_이력은_정리된다() {
        stubDiscovery(server(1L, "alpha", ServerState.ACTIVE));
        when(gameServerClient.healthcheck(ALPHA_URL)).thenReturn(Mono.just(true));
        refresh();
        assertThat(serverHealthRegistry.isHealthy(1L)).isTrue();

        serverHealthRegistry.replaceServers(List.of());

        assertThat(serverHealthRegistry.isHealthy(1L)).isFalse();
        assertThat(gameServerManagementService.getAvailableServers()).isEmpty();
    }
}
