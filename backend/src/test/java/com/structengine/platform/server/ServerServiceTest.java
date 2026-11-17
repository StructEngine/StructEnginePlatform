package com.structengine.platform.server;

import com.structengine.platform.account.AccountType;
import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.game.ws.GameServerRegistry;
import com.structengine.platform.id.SnowflakeIdGenerator;
import com.structengine.platform.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerServiceTest {

    private static final AuthPrincipal USER =
            new AuthPrincipal(AccountType.USER, 7L, "Steve", "USER", 0);
    private static final AuthPrincipal ADMIN =
            new AuthPrincipal(AccountType.ADMIN, 1L, "admin", "ADMIN", 0);

    @Mock
    private MinecraftServerRepository repository;

    @Mock
    private GameServerRegistry registry;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    private ServerService service;

    @BeforeEach
    void setUp() {
        service = new ServerService(repository, registry, idGenerator);
    }

    private static MinecraftServer server(long owner, String status) {
        long now = 1000L;
        Long registeredAt = "PENDING".equals(status) ? null : now;
        return new MinecraftServer(10L, owner, "生存一服",
                ServerSecrets.hash("old-secret"), status, null, null,
                now, registeredAt, null, null, now, now);
    }

    @Test
    void createsPendingServerAndReturnsSecretOnce() {
        when(idGenerator.nextId()).thenReturn(10L);
        when(registry.isConnected("10")).thenReturn(false);

        ServerCreated created = service.create(USER, " 生存一服 ", "https://platform.example.com");

        assertEquals("10", created.server().serverId());
        assertEquals("生存一服", created.server().serverName());
        assertEquals("PENDING", created.server().status());
        assertFalse(created.secret().isBlank());
        assertEquals("wss://platform.example.com/ws/game", created.wsUrl());

        ArgumentCaptor<MinecraftServer> captor = ArgumentCaptor.forClass(MinecraftServer.class);
        verify(repository).insert(captor.capture());
        assertNotEquals(created.secret(), captor.getValue().secretHash());
        assertTrue(ServerSecrets.matches(created.secret(), captor.getValue().secretHash()));
    }

    @Test
    void adminCannotOwnServer() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(ADMIN, "test", "http://localhost"));
        assertEquals(ErrorCode.FORBIDDEN, error.errorCode());
        verify(repository, never()).insert(any());
    }

    @Test
    void hidesOtherUsersServerAsNotFound() {
        when(repository.findById(10L)).thenReturn(Optional.of(server(8L, "OFFLINE")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.get(USER, 10L));
        assertEquals(ErrorCode.NOT_FOUND, error.errorCode());
    }

    @Test
    void adminCanAccessAnyServer() {
        when(repository.findById(10L)).thenReturn(Optional.of(server(8L, "OFFLINE")));
        when(registry.isConnected("10")).thenReturn(false);

        assertEquals("生存一服", service.get(ADMIN, 10L).serverName());
    }

    @Test
    void rotatesSecretAndDisconnectsOldSession() {
        when(repository.findById(10L)).thenReturn(Optional.of(server(7L, "ONLINE")));

        ServerSecretRotated rotated = service.rotateSecret(USER, 10L, "http://localhost:8080");

        assertFalse(rotated.secret().isBlank());
        assertNotEquals("old-secret", rotated.secret());
        assertEquals("ws://localhost:8080/ws/game", rotated.wsUrl());
        verify(repository).updateSecret(eqId(10L), anyString(), anyLong(), anyLong());
        verify(registry).disconnect("10", "secret rotated");
        verify(repository).updateStatus(eqId(10L), eq("OFFLINE"), anyLong());
    }

    @Test
    void findsServerBySecretOnlyWhenUsable() {
        MinecraftServer active = server(7L, "OFFLINE");
        String hash = active.secretHash();
        when(repository.findBySecretHash(hash)).thenReturn(Optional.of(active));
        assertTrue(service.findBySecret("old-secret").isPresent());

        MinecraftServer disabled = server(7L, "DISABLED");
        when(repository.findBySecretHash(hash)).thenReturn(Optional.of(disabled));
        assertTrue(service.findBySecret("old-secret").isEmpty());
    }

    @Test
    void markOnlineAndOfflineUpdateRepository() {
        service.markOnline(10L, "http://map", "world");
        verify(repository).updateRegistration(eqId(10L), eq("http://map"), eq("world"),
                anyLong(), anyLong(), anyLong());

        MinecraftServer active = server(7L, "ONLINE");
        when(repository.findById(10L)).thenReturn(Optional.of(active));
        service.markOffline(10L);
        verify(repository).updateStatus(eqId(10L), eq("OFFLINE"), anyLong());
    }

    @Test
    void listsOwnServersForUserAndAllForAdmin() {
        when(repository.listByOwner(7L)).thenReturn(List.of(server(7L, "OFFLINE")));
        when(repository.listAll()).thenReturn(List.of(server(7L, "OFFLINE")));
        when(registry.isConnected("10")).thenReturn(false);

        assertEquals(1, service.list(USER).size());
        assertEquals(1, service.list(ADMIN).size());
        verify(repository).listByOwner(7L);
        verify(repository).listAll();
    }

    @Test
    void buildsPluginWsUrl() {
        assertEquals("/ws/game", ServerService.wsUrl(""));
        assertEquals("ws://host/ws/game", ServerService.wsUrl("http://host"));
        assertEquals("wss://host/base/ws/game", ServerService.wsUrl("https://host/base/"));
    }

    private static long eqId(long value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
