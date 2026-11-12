package com.tracewitness.platform.profile;

import com.tracewitness.platform.profile.ProfilePolicy.Decision;
import com.tracewitness.platform.profile.ProfilePolicy.GalleryProfile;
import com.tracewitness.platform.profile.ProfilePolicy.SampleWeight;
import com.tracewitness.platform.quant.Quantities;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.PlatformStore.RunManifest;
import com.tracewitness.platform.store.ProfileStore;
import com.tracewitness.platform.time.TimeSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProfileService {
    private final ProfileStore profiles;
    private final PlatformStore platform;
    private final ScenarioService scenarios;
    private final TimeSource timeSource;
    private final TransactionTemplate transactions;

    public ProfileService(ProfileStore profiles, PlatformStore platform, ScenarioService scenarios,
                          TimeSource timeSource, TransactionTemplate transactions) {
        this.profiles = profiles;
        this.platform = platform;
        this.scenarios = scenarios;
        this.timeSource = timeSource;
        this.transactions = transactions;
    }

    public void ensure() {
        final String runId = scenarios.runId();
        if (profiles.countProfiles(runId) > 0) {
            return;
        }
        final RunManifest manifest = platform.findManifest(runId);
        if (manifest == null) {
            return;
        }
        transactions.execute(status -> {
            if (profiles.countProfiles(runId) > 0) {
                return null;
            }
            seed(runId, manifest);
            return null;
        });
    }

    public Map<String, Object> overview() {
        String runId = scenarios.runId();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("doors", profiles.listDoors(runId));
        body.put("edges", profiles.listEdges(runId));
        body.put("profiles", profiles.listProfiles(runId));
        body.put("tickets", profiles.listTickets(runId));
        body.put("audits", profiles.listAudits(runId));
        return body;
    }

    public List<Map<String, Object>> search(String runId, String query) {
        String needle = query == null ? "" : query.trim();
        List<Map<String, Object>> hits = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> profile : profiles.listProfiles(runId)) {
            if (!"active".equals(profile.get("status"))) {
                continue;
            }
            if (needle.length() > 0 && !matches(profile, needle)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", profile.get("id"));
            row.put("displayName", profile.get("displayName"));
            row.put("gallery", profile.get("gallery"));
            row.put("addressLabel", profile.get("addressLabel"));
            row.put("visitCount", profile.get("visitCount"));
            hits.add(row);
        }
        return hits;
    }

    public Map<String, Object> decideTicket(String ticketId, String decision, String actor) {
        Map<String, Object> ticket = profiles.findTicket(ticketId);
        if (ticket == null || !scenarios.runId().equals(ticket.get("runId"))) {
            return null;
        }
        Map<String, Object> outcome = ProfilePolicy.approval(String.valueOf(ticket.get("status")), decision);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("applied", Boolean.FALSE);
        body.put("ticket", ticket);
        if (!"pending_human".equals(ticket.get("status")) || "pending_human".equals(outcome.get("status"))) {
            return body;
        }
        String runId = String.valueOf(ticket.get("runId"));
        RunManifest manifest = platform.findManifest(runId);
        String stamped = stamp(manifest);
        String who = actor == null || actor.trim().isEmpty() ? "investigator" : actor.trim();
        transactions.execute(status -> {
            Map<String, Object> current = profiles.findTicket(ticketId);
            if (current == null || !"pending_human".equals(current.get("status"))) {
                return null;
            }
            String next = String.valueOf(outcome.get("status"));
            profiles.updateTicketStatus(ticketId, next);
            if ("approved".equals(next)) {
                String rightId = (String) current.get("rightProfileId");
                String leftId = (String) current.get("leftProfileId");
                if (rightId != null && leftId != null) {
                    profiles.reassignSamples(rightId, leftId);
                    profiles.markMerged(rightId, leftId);
                    refresh(leftId);
                }
                profiles.insertAudit(platform.nextId(runId, "audit"), runId, "merge_approved", ticketId,
                        String.valueOf(current.get("detail")), who, stamped);
            } else {
                profiles.insertAudit(platform.nextId(runId, "audit"), runId, "merge_rejected", ticketId,
                        String.valueOf(current.get("detail")), who, stamped);
            }
            return null;
        });
        body.put("applied", Boolean.TRUE);
        body.put("ticket", profiles.findTicket(ticketId));
        return body;
    }

    private void seed(String runId, RunManifest manifest) {
        String stamped = stamp(manifest);
        for (DoorGraph.Node node : DoorGraph.NODES) {
            profiles.insertDoor(platform.nextId(runId, "door"), runId, node.code, node.name);
        }
        for (DoorGraph.Edge edge : DoorGraph.EDGES) {
            profiles.insertEdge(platform.nextId(runId, "edge"), runId, edge.from, edge.to, edge.seconds);
        }
        enroll(runId, "resident-chen", "resident", "居民陈", "unit-a", "1.00,0.00,0.00", "0.90", "enroll-chen");
        sighting(runId, manifest, stamped, "resident-return", "0.86", "0.98,0.02,0.00", "lobby", "unit-a", 10, 50,
                "居民陈在旅行时间内从大堂到一单元门");
        sighting(runId, manifest, stamped, "stranger-first", "0.70", "0.00,1.00,0.00", "lobby", null, 300, 300,
                "大堂新出现陌生人");
        sighting(runId, manifest, stamped, "stranger-second", "0.70", "0.00,1.00,0.00", "lobby", null, 310, 310,
                "陌生人再次经过大堂");
        sighting(runId, manifest, stamped, "stranger-third", "0.70", "0.00,1.00,0.00", "lobby", null, 320, 320,
                "陌生人第三次经过大堂");
        openFrequencyTicket(runId, stamped);
        enroll(runId, "lookalike", "stranger", "相似陌生人", null, "0.98,0.20,0.00", "0.80", "enroll-lookalike");
        sighting(runId, manifest, stamped, "roof-jump", "0.88", "0.99,0.01,0.00", "lobby", "roof", 400, 401,
                "屋顶与大堂之间没有通路");
        sighting(runId, manifest, stamped, "fast-unit-b", "0.91", "0.99,0.01,0.00", "lobby", "unit-b", 500, 510,
                "到达二单元门快于最短路");
        sighting(runId, manifest, stamped, "ambiguous-pair", "0.84", "0.995,0.100,0.000", "lobby", null, 600, 600,
                "居民档和相似陌生人分数过近");
    }

    private void enroll(String runId, String code, String gallery, String name, String address, String vector,
                        String quality, String caseKey) {
        BigDecimal faceQuality = new BigDecimal(quality);
        String id = platform.nextId(runId, "profile");
        profiles.insertProfile(id, runId, code, gallery, name, address, vector, faceQuality, "active", 0);
        profiles.insertSample(platform.nextId(runId, "sample"), runId, id, caseKey, faceQuality, vector, true);
    }

    private void sighting(String runId, RunManifest manifest, String stamped, String caseKey, String quality,
                          String vector, String fromDoor, String toDoor, long tickFrom, long tickTo, String detail) {
        Decision decision = ProfilePolicy.decide(new BigDecimal(quality), ProfilePolicy.parseVector(vector), fromDoor,
                toDoor, tickFrom, tickTo, manifest.getTickMs(), activeGalleries(runId));
        String profileId = null;
        boolean bound = false;
        if (decision.profileCreated) {
            profileId = platform.nextId(runId, "profile");
            String code = "stranger-first".equals(caseKey) ? "stranger-jia" : caseKey;
            profiles.insertProfile(profileId, runId, code, "stranger", "陌生人甲", null, vector,
                    new BigDecimal(quality), "active", 0);
            profiles.insertAudit(platform.nextId(runId, "audit"), runId, "profile_created", profileId, detail, "system",
                    stamped);
            bound = true;
        } else if (decision.matchedId != null && ("auto".equals(decision.merge))) {
            profileId = idOf(runId, decision.matchedId);
            bound = true;
        }
        String candidateId = profileId;
        if (candidateId == null && decision.matchedId != null) {
            candidateId = idOf(runId, decision.matchedId);
        }
        String sampleId = platform.nextId(runId, "sample");
        profiles.insertSample(sampleId, runId, bound ? profileId : candidateId, caseKey, new BigDecimal(quality), vector,
                bound);
        bindDoor(runId, manifest, caseKey, fromDoor, tickFrom, sampleId, candidateId, bound);
        if (toDoor != null && !toDoor.equals(fromDoor)) {
            bindDoor(runId, manifest, caseKey, toDoor, tickTo, sampleId, candidateId, bound);
        }
        if (bound && profileId != null) {
            refresh(profileId);
        }
        if ("none".equals(decision.merge) && !decision.profileCreated) {
            return;
        }
        if ("none".equals(decision.merge)) {
            return;
        }
        String left = decision.matchedId == null ? profileId : idOf(runId, decision.matchedId);
        String right = decision.secondId == null ? null : idOf(runId, decision.secondId);
        String ticketId = platform.nextId(runId, "ticket");
        profiles.insertTicket(ticketId, runId, caseKey, "merge", left, right, decision.merge, decision.reason,
                decision.observedSeconds, decision.travelSeconds, detail, stamped);
        profiles.insertAudit(platform.nextId(runId, "audit"), runId, auditAction(decision.merge), ticketId, detail,
                "system", stamped);
    }

    private void bindDoor(String runId, RunManifest manifest, String caseKey, String door, long tick, String sampleId,
                          String profileId, boolean bound) {
        String accessId = platform.nextId(runId, "access");
        profiles.insertAccess(accessId, runId, caseKey, door, tick, sampleId, at(manifest, tick));
        profiles.insertBinding(platform.nextId(runId, "binding"), runId, profileId, accessId, sampleId,
                bound ? "bound" : "rejected");
    }

    private void openFrequencyTicket(String runId, String stamped) {
        Map<String, Object> stranger = profiles.findProfileByCode(runId, "stranger-jia");
        if (stranger == null || !ProfilePolicy.frequency((Integer) stranger.get("visitCount"))) {
            return;
        }
        String ticketId = platform.nextId(runId, "ticket");
        String detail = stranger.get("addressLabel") + " 出现 " + stranger.get("visitCount") + " 次";
        profiles.insertTicket(ticketId, runId, "stranger-frequency", "address", String.valueOf(stranger.get("id")),
                null, "open", "frequent_door", null, null, detail, stamped);
        profiles.insertAudit(platform.nextId(runId, "audit"), runId, "address_ticket", ticketId, detail, "system",
                stamped);
    }

    private void refresh(String profileId) {
        List<Map<String, Object>> samples = profiles.listBoundSamples(profileId);
        List<SampleWeight> weights = new ArrayList<SampleWeight>();
        BigDecimal qualitySum = BigDecimal.ZERO;
        for (Map<String, Object> sample : samples) {
            BigDecimal quality = (BigDecimal) sample.get("faceQuality");
            qualitySum = qualitySum.add(quality);
            weights.add(new SampleWeight(quality, ProfilePolicy.parseVector(String.valueOf(sample.get("vector")))));
        }
        List<BigDecimal> center = ProfilePolicy.centroid(weights);
        BigDecimal quality = Quantities.persisted(qualitySum.divide(new BigDecimal(samples.size()), 8,
                java.math.RoundingMode.HALF_UP));
        Map<String, Integer> visits = new LinkedHashMap<String, Integer>();
        for (String door : profiles.listBoundDoors(profileId)) {
            Integer count = visits.get(door);
            visits.put(door, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }
        Map<String, Object> profile = profiles.findProfile(profileId);
        String address = ProfilePolicy.addressLabel((String) profile.get("addressLabel"), visits);
        int visitCount = 0;
        for (Integer count : visits.values()) {
            visitCount += count.intValue();
        }
        profiles.updateProfile(profileId, address, ProfilePolicy.formatVector(center), quality, visitCount);
    }

    private List<GalleryProfile> activeGalleries(String runId) {
        List<GalleryProfile> galleries = new ArrayList<GalleryProfile>();
        for (Map<String, Object> profile : profiles.listProfiles(runId)) {
            if (!"active".equals(profile.get("status"))) {
                continue;
            }
            galleries.add(new GalleryProfile(String.valueOf(profile.get("code")), String.valueOf(profile.get("gallery")),
                    ProfilePolicy.parseVector(String.valueOf(profile.get("centroid")))));
        }
        return galleries;
    }

    private String idOf(String runId, String code) {
        Map<String, Object> profile = profiles.findProfileByCode(runId, code);
        if (profile == null) {
            throw new IllegalStateException("missing profile " + code);
        }
        return String.valueOf(profile.get("id"));
    }

    private static boolean matches(Map<String, Object> profile, String needle) {
        return contains(profile.get("displayName"), needle)
                || contains(profile.get("addressLabel"), needle)
                || contains(profile.get("gallery"), needle)
                || contains(profile.get("code"), needle);
    }

    private static boolean contains(Object value, String needle) {
        return value != null && String.valueOf(value).contains(needle);
    }

    private static String auditAction(String merge) {
        if ("auto".equals(merge)) {
            return "merge_auto";
        }
        if ("forbidden".equals(merge)) {
            return "merge_forbidden";
        }
        if ("pending_human".equals(merge)) {
            return "merge_pending";
        }
        return "merge_recorded";
    }

    private static String stamp(RunManifest manifest) {
        return TimeSource.FORMAT.format(Instant.parse(manifest.getEpochStart()).plusMillis(
                Math.multiplyExact(manifest.getTick(), manifest.getTickMs())));
    }

    private static String at(RunManifest manifest, long tick) {
        return TimeSource.FORMAT.format(Instant.parse(manifest.getEpochStart()).plusMillis(
                Math.multiplyExact(tick, manifest.getTickMs())));
    }
}
