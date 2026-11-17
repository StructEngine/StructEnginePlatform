package com.structengine.platform.task;

import com.structengine.platform.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildTaskServiceTest {

    @Mock
    private BuildTaskRepository repository;

    private BuildTaskService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setTaskTimeoutSeconds(600);
        service = new BuildTaskService(repository, props);
    }

    @Test
    void marksStaleTasksFailed() {
        when(repository.markStaleFailed(anyLong(), anyLong(), anyString())).thenReturn(2);

        service.failStaleTasks();

        verify(repository).markStaleFailed(anyLong(), anyLong(), anyString());
    }

    @Test
    void abortsInterruptedTasks() {
        when(repository.markInterrupted(anyLong(), anyString())).thenReturn(1);

        service.abortInterruptedTasks();

        verify(repository).markInterrupted(anyLong(), anyString());
    }
}
