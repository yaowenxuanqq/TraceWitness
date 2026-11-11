package com.tracewitness.platform.agent;

import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.profile.ProfileService;
import com.tracewitness.platform.safety.ReverseRules;
import com.tracewitness.platform.store.TrackStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InvestigationTools {
    public static final List<String> NAMES = Collections.unmodifiableList(java.util.Arrays.asList(
            "search_events", "get_event_detail", "search_person", "build_trajectory", "find_companions", "export_evidence"));

    private final PlatformStore store;
    private final TrackStore tracks;
    private final ProfileService profiles;

    public InvestigationTools(PlatformStore store, TrackStore tracks, ProfileService profiles) {
        this.store = store;
        this.tracks = tracks;
        this.profiles = profiles;
    }

    public Map<String, Object> execute(String runId, String toolName, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? new LinkedHashMap<String, Object>() : arguments;
        if ("search_events".equals(toolName)) {
            return searchEvents(runId, args);
        }
        if ("get_event_detail".equals(toolName)) {
            return eventDetail(runId, args);
        }
        if ("search_person".equals(toolName)) {
            return searchPerson(runId, args);
        }
        if ("build_trajectory".equals(toolName)) {
            return trajectory(runId);
        }
        if ("find_companions".equals(toolName)) {
            Map<String, Object> result = base("find_companions");
            result.put("companions", Collections.emptyList());
            result.put("source_ids", Collections.emptyList());
            return result;
        }
        if ("export_evidence".equals(toolName)) {
            return failed(toolName, "evidence_export_pending");
        }
        return failed(toolName, "unknown_tool");
    }

    private Map<String, Object> searchEvents(String runId, Map<String, Object> args) {
        int limit = 5;
        if (args.get("limit") instanceof Number) {
            limit = Math.min(20, Math.max(1, ((Number) args.get("limit")).intValue()));
        }
        List<Map<String, Object>> events = store.listEvents(runId, limit);
        List<Map<String, Object>> compact = new ArrayList<Map<String, Object>>();
        List<String> sourceIds = new ArrayList<String>();
        for (Map<String, Object> event : events) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", event.get("id"));
            row.put("eventType", event.get("eventType"));
            row.put("summary", event.get("summary"));
            row.put("tick", event.get("tick"));
            compact.add(row);
            sourceIds.add(String.valueOf(event.get("id")));
        }
        Map<String, Object> result = base("search_events");
        result.put("events", compact);
        result.put("source_ids", sourceIds);
        return result;
    }

    private Map<String, Object> eventDetail(String runId, Map<String, Object> args) {
        String eventId = args.get("event_id") == null ? "" : String.valueOf(args.get("event_id"));
        Map<String, Object> event = store.findEvent(eventId);
        if (event == null || !runId.equals(event.get("runId"))) {
            return failed("get_event_detail", "event_not_found");
        }
        Map<String, Object> result = base("get_event_detail");
        result.put("event", event);
        result.put("source_ids", Collections.singletonList(eventId));
        return result;
    }

    private Map<String, Object> searchPerson(String runId, Map<String, Object> args) {
        String query = args.get("query") == null ? "" : String.valueOf(args.get("query"));
        List<Map<String, Object>> people = profiles.search(runId, query);
        Map<String, Object> rule = ReverseRules.evaluate("empty_search",
                Collections.<String, Object>singletonMap("hits", people));
        List<String> sourceIds = new ArrayList<String>();
        for (Map<String, Object> person : people) {
            sourceIds.add(String.valueOf(person.get("id")));
        }
        Map<String, Object> result = base("search_person");
        result.put("people", people);
        result.put("person_created", rule.get("person_created"));
        result.put("source_ids", sourceIds);
        return result;
    }

    private Map<String, Object> trajectory(String runId) {
        Map<String, Object> result = base("build_trajectory");
        List<Map<String, Object>> compact = new ArrayList<Map<String, Object>>();
        List<String> sourceIds = new ArrayList<String>();
        for (Map<String, Object> track : tracks.listTracks(runId)) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", track.get("id"));
            row.put("pointCount", track.get("pointCount"));
            row.put("openedTick", track.get("openedTick"));
            row.put("closedTick", track.get("closedTick"));
            compact.add(row);
            sourceIds.add(String.valueOf(track.get("id")));
        }
        result.put("tracks", compact);
        result.put("source_ids", sourceIds);
        return result;
    }

    private static Map<String, Object> base(String toolName) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("tool_name", toolName);
        return result;
    }

    private static Map<String, Object> failed(String toolName, String error) {
        Map<String, Object> result = base(toolName);
        result.put("error", error);
        result.put("source_ids", Collections.emptyList());
        return result;
    }
}
