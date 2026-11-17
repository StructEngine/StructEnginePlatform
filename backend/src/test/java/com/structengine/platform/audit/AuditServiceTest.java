package com.structengine.platform.audit;

import com.structengine.platform.account.AccountType;
import com.structengine.platform.id.SnowflakeIdGenerator;
import com.structengine.platform.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditRepository repository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(repository, idGenerator);
    }

    @Test
    void recordsAuditEntry() {
        when(idGenerator.nextId()).thenReturn(100L);

        service.record(new AuthPrincipal(AccountType.USER, 7L, "Steve", "USER", 0),
                "BUILD_DEPLOY", 9L, 10L, 11L, 1, 64, 2, "127.0.0.1", "SUCCESS", "rotation=0");

        verify(repository).insert(any(AuditLog.class));
    }
}
