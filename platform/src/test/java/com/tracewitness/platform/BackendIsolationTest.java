package com.tracewitness.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracewitness.platform.agent.PythonAgentClient;
import com.tracewitness.platform.config.AppProperties;
import com.tracewitness.platform.media.MediaService;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.MediaStore;
import com.tracewitness.platform.store.ObservationStore;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.TrackStore;
import com.tracewitness.platform.time.TimeSource;
import com.tracewitness.platform.vision.StructureService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackendIsolationTest {
    @Test
    void mediaLookupsAreScopedToCurrentRun() throws Exception {
        MediaStore mediaStore = mock(MediaStore.class);
        ScenarioService scenarios = mock(ScenarioService.class);
        when(scenarios.runId()).thenReturn("run-current");
        MediaService service = new MediaService(mediaStore, mock(PlatformStore.class), scenarios,
                mock(AppProperties.class), mock(TimeSource.class), mock(org.springframework.transaction.support.TransactionTemplate.class));
        when(mediaStore.findAsset("asset-foreign")).thenReturn(row("run-foreign"));
        when(mediaStore.findSegment("segment-foreign")).thenReturn(row("run-foreign"));

        assertNull(service.findAsset("asset-foreign"));
        assertNull(service.readFrame("segment-foreign", 0));
    }

    @Test
    void thumbnailLookupIsScopedToCurrentRun() throws Exception {
        ObservationStore observations = mock(ObservationStore.class);
        ScenarioService scenarios = mock(ScenarioService.class);
        when(scenarios.runId()).thenReturn("run-current");
        when(observations.find("observation-foreign")).thenReturn(row("run-foreign"));
        StructureService service = new StructureService(observations, mock(TrackStore.class),
                mock(PlatformStore.class), scenarios, mock(PythonAgentClient.class), mock(TimeSource.class),
                mock(TransactionTemplate.class), mock(AppProperties.class), mock(ObjectMapper.class));

        assertNull(service.readThumbnail("observation-foreign"));
    }

    private static Map<String, Object> row(String runId) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", runId);
        return row;
    }
}
