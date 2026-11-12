package com.tracewitness.platform;

import com.tracewitness.platform.api.MediaController;
import com.tracewitness.platform.api.SecurityHeadersFilter;
import com.tracewitness.platform.media.MediaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MediaController.class)
@Import(SecurityHeadersFilter.class)
class MediaControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private MediaService media;

    @Test
    void assetResponseCarriesBrowserHardeningHeaders() throws Exception {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("contentType", "audio/wav");
        row.put("storagePath", "sample.wav");
        when(media.findAsset("asset-1")).thenReturn(row);
        when(media.readStored(row)).thenReturn(new byte[]{1, 2, 3});

        mvc.perform(get("/api/media/assets/asset-1/file"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")));
    }

    @Test
    void uploadRejectsNonMediaContent() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "evil.html", "text/html",
                "<html></html>".getBytes(StandardCharsets.UTF_8));
        when(media.storeUpload(anyString(), anyString(), any(byte[].class)))
                .thenThrow(new IllegalArgumentException("unsupported media type"));

        mvc.perform(multipart("/api/media/uploads").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported media type"));
    }
}
