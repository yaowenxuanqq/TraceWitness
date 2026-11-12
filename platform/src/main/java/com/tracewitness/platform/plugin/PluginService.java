package com.tracewitness.platform.plugin;

import com.tracewitness.platform.plugin.PluginPolicy.Verdict;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.PlatformStore.RunManifest;
import com.tracewitness.platform.store.PluginStore;
import com.tracewitness.platform.store.ProfileStore;
import com.tracewitness.platform.time.TimeSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PluginService {
    private final PluginStore plugins;
    private final PlatformStore platform;
    private final ProfileStore profiles;
    private final ScenarioService scenarios;
    private final TimeSource timeSource;
    private final TransactionTemplate transactions;

    public PluginService(PluginStore plugins, PlatformStore platform, ProfileStore profiles, ScenarioService scenarios,
                         TimeSource timeSource, TransactionTemplate transactions) {
        this.plugins = plugins;
        this.platform = platform;
        this.profiles = profiles;
        this.scenarios = scenarios;
        this.timeSource = timeSource;
        this.transactions = transactions;
    }

    public void ensure() {
        final String runId = scenarios.runId();
        if (plugins.count(runId) > 0) {
            return;
        }
        final RunManifest manifest = platform.findManifest(runId);
        if (manifest == null) {
            return;
        }
        transactions.execute(status -> {
            if (plugins.count(runId) > 0) {
                return null;
            }
            seed(runId);
            return null;
        });
    }

    public Map<String, Object> overview() {
        String runId = scenarios.runId();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("plugins", plugins.listManifests(runId));
        body.put("health", plugins.listHealth(runId));
        body.put("rollbacks", plugins.listRollbacks(runId));
        return body;
    }

    public Map<String, Object> fault(String id) {
        Map<String, Object> row = plugins.findManifest(id);
        if (row == null || !scenarios.runId().equals(row.get("runId"))) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (!"active".equals(row.get("slot"))) {
            result.put("applied", Boolean.FALSE);
            result.put("reason", "not_active");
            return result;
        }
        String runId = String.valueOf(row.get("runId"));
        return transactions.execute(status -> applyFault(runId, row));
    }

    public Map<String, Object> rollback(String actor) {
        String runId = scenarios.runId();
        Map<String, Object> active = plugins.findSlot(runId, "active");
        Map<String, Object> previous = plugins.findSlot(runId, "previous");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (active == null || previous == null) {
            result.put("applied", Boolean.FALSE);
            result.put("reason", "no_previous");
            return result;
        }
        String fromId = String.valueOf(active.get("id"));
        String toId = String.valueOf(previous.get("id"));
        transactions.execute(status -> {
            plugins.updateSlot(fromId, "previous");
            plugins.updateSlot(toId, "active");
            plugins.insertRollback(platform.nextId(runId, "rollback"), runId, fromId, toId, "manual", actor);
            profiles.insertAudit(platform.nextId(runId, "audit"), runId, "plugin_rollback", fromId, toId, actor,
                    timeSource.formatNow());
            return null;
        });
        result.put("applied", Boolean.TRUE);
        result.put("fromId", fromId);
        result.put("toId", toId);
        return result;
    }

    private void seed(String runId) {
        admit(runId, "door-watch", "1.0.0", "entry=watch\n", 16, PluginPolicy.ARCH, PluginPolicy.ABI, "");
        admit(runId, "door-watch", "1.1.0", "entry=watch-v2\n", 16, PluginPolicy.ARCH, PluginPolicy.ABI, "");
        admit(runId, "door-watch", "1.2.0", "entry=forged\n", 16, PluginPolicy.ARCH, PluginPolicy.ABI, "signature");
        admit(runId, "crowd-count", "0.9.0", "entry=crowd\n", 128, PluginPolicy.ARCH, PluginPolicy.ABI, "");
    }

    private void admit(String runId, String name, String version, String payload, int resourceMb, String arch,
                       String abi, String tamper) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        String hash = PluginPolicy.sha256(bytes);
        if ("hash".equals(tamper)) {
            hash = PluginPolicy.sha256("other");
        }
        String signature = PluginPolicy.sign(PluginPolicy.canonical(name, version, arch, abi, resourceMb, hash));
        if ("signature".equals(tamper)) {
            signature = signature.substring(0, signature.length() - 1) + (signature.endsWith("0") ? "1" : "0");
        }
        Verdict verdict = PluginPolicy.inspect(name, version, arch, abi, resourceMb, hash, signature, bytes);
        String slot = slotFor(verdict);
        String id = platform.nextId(runId, "plugin");
        plugins.insertManifest(id, runId, name, version, arch, abi, resourceMb, hash, signature, payload, slot,
                verdict.reason, verdict.health, verdict.loaded, verdict.hashChecked, PluginPolicy.CHECK_ORDER);
        plugins.insertHealth(platform.nextId(runId, "health"), runId, id, verdict.health, verdict.signatureOk,
                verdict.hashOk, verdict.abiOk, verdict.resourceOk);
        if (PluginPolicy.promote(verdict)) {
            Map<String, Object> previous = plugins.findSlot(runId, "previous");
            if (previous != null) {
                plugins.updateSlot(String.valueOf(previous.get("id")), "retired");
            }
            Map<String, Object> active = plugins.findSlot(runId, "active");
            if (active != null && !id.equals(active.get("id"))) {
                plugins.updateSlot(String.valueOf(active.get("id")), "previous");
            }
            plugins.updateSlot(id, "active");
            profiles.insertAudit(platform.nextId(runId, "audit"), runId, "plugin_promoted", id,
                    name + " " + version, "system", timeSource.formatNow());
        } else {
            profiles.insertAudit(platform.nextId(runId, "audit"), runId, "plugin_rejected", id, verdict.reason,
                    "system", timeSource.formatNow());
        }
    }

    private Map<String, Object> applyFault(String runId, Map<String, Object> row) {
        String id = String.valueOf(row.get("id"));
        byte[] payload = String.valueOf(row.get("payload")).getBytes(StandardCharsets.UTF_8);
        Verdict verdict = PluginPolicy.inspect(String.valueOf(row.get("name")), String.valueOf(row.get("version")),
                String.valueOf(row.get("arch")), String.valueOf(row.get("abi")),
                ((Number) row.get("resourceMb")).intValue(), String.valueOf(row.get("payloadHash")), "0", payload);
        plugins.updateOutcome(id, "0", "rejected", verdict.reason, verdict.health, verdict.loaded, verdict.hashChecked);
        plugins.insertHealth(platform.nextId(runId, "health"), runId, id, verdict.health, verdict.signatureOk,
                verdict.hashOk, verdict.abiOk, verdict.resourceOk);
        Map<String, Object> previous = plugins.findSlot(runId, "previous");
        String toId = null;
        if (previous != null) {
            toId = String.valueOf(previous.get("id"));
            plugins.updateSlot(toId, "active");
        }
        plugins.insertRollback(platform.nextId(runId, "rollback"), runId, id, toId, "fault", "fault-injection");
        profiles.insertAudit(platform.nextId(runId, "audit"), runId, "plugin_fault", id,
                toId == null ? "no_previous" : toId, "fault-injection", timeSource.formatNow());
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("applied", Boolean.TRUE);
        result.put("loaded", Boolean.valueOf(verdict.loaded));
        result.put("reason", verdict.reason);
        result.put("hashChecked", Boolean.valueOf(verdict.hashChecked));
        result.put("toId", toId);
        return result;
    }

    private static String slotFor(Verdict verdict) {
        if ("resource_rejected".equals(verdict.reason)) {
            return "candidate";
        }
        if (verdict.loaded) {
            return "candidate";
        }
        return "rejected";
    }
}
