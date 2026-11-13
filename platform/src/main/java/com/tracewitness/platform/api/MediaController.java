package com.tracewitness.platform.api;

import com.tracewitness.platform.media.MediaService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @GetMapping
    public Map<String, Object> timeline() {
        return media.timeline();
    }

    @GetMapping("/assets/{id}/file")
    public ResponseEntity<byte[]> asset(@PathVariable String id) throws IOException {
        Map<String, Object> row = media.findAsset(id);
        if (row == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] body = media.readStored(row);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(String.valueOf(row.get("contentType"))))
                .body(body);
    }

    @GetMapping("/segments/{id}/frames/{index}")
    public ResponseEntity<byte[]> frame(@PathVariable String id, @PathVariable int index) throws IOException {
        byte[] body = media.readFrame(id, index);
        if (body == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(body);
    }

    @PostMapping("/uploads")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(media.storeUpload(file));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Collections.<String, Object>singletonMap("error", exception.getMessage()));
        }
    }
}
