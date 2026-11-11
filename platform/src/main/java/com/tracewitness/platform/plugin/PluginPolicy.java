package com.tracewitness.platform.plugin;

import com.tracewitness.platform.quant.Quantities;
import com.tracewitness.platform.safety.ReverseRules;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PluginPolicy {
    public static final String ARCH = "java8";
    public static final String ABI = "tool-v1";
    public static final int RESOURCE_MAX_MB = 64;
    public static final BigDecimal PROMOTE_MIN = new BigDecimal("0.80");
    public static final String CHECK_ORDER = "signature,hash,abi,resource";
    private static final String SIGN_KEY = "tracewitness-plugin-w11";

    private PluginPolicy() {
    }

    public static Map<String, Object> evaluate(Map<String, Object> input) {
        String name = text(input.get("name"), "door-watch");
        String version = text(input.get("version"), "1.0.0");
        String arch = text(input.get("arch"), ARCH);
        String abi = text(input.get("abi"), ABI);
        int resourceMb = Integer.parseInt(String.valueOf(input.get("resource_mb")));
        byte[] payload = String.valueOf(input.get("payload")).getBytes(StandardCharsets.UTF_8);
        String hash = sha256(payload);
        if ("hash".equals(input.get("tamper"))) {
            hash = sha256("other".getBytes(StandardCharsets.UTF_8));
        }
        String signature = sign(canonical(name, version, arch, abi, resourceMb, hash));
        if ("signature".equals(input.get("tamper"))) {
            signature = flip(signature);
        }
        return inspect(name, version, arch, abi, resourceMb, hash, signature, payload).toMap();
    }

    public static Verdict inspect(String name, String version, String arch, String abi, int resourceMb,
                                  String claimedHash, String signature, byte[] payload) {
        String expected = sign(canonical(name, version, arch, abi, resourceMb, claimedHash));
        boolean signatureOk = expected.equals(signature);
        if (!signatureOk) {
            Map<String, Object> ruled = ReverseRules.evaluate("bad_signature",
                    Collections.<String, Object>singletonMap("signature_valid", Boolean.FALSE));
            return new Verdict(false, false, false, false, false,
                    Boolean.TRUE.equals(ruled.get("loaded")), "bad_signature", new BigDecimal("0.00"));
        }
        boolean hashOk = sha256(payload).equals(claimedHash);
        if (!hashOk) {
            return new Verdict(true, true, false, false, false, false, "hash_mismatch", new BigDecimal("0.25"));
        }
        boolean abiOk = ARCH.equals(arch) && ABI.equals(abi);
        if (!abiOk) {
            return new Verdict(true, true, true, false, false, false, "abi_rejected", new BigDecimal("0.40"));
        }
        boolean resourceOk = resourceMb >= 1 && resourceMb <= RESOURCE_MAX_MB;
        if (!resourceOk) {
            return new Verdict(true, true, true, true, false, false, "resource_rejected", new BigDecimal("0.55"));
        }
        Map<String, Object> ruled = ReverseRules.evaluate("bad_signature",
                Collections.<String, Object>singletonMap("signature_valid", Boolean.TRUE));
        return new Verdict(true, true, true, true, true, Boolean.TRUE.equals(ruled.get("loaded")),
                "accepted", new BigDecimal("1.00"));
    }

    public static boolean promote(Verdict verdict) {
        return verdict.loaded && verdict.health.compareTo(PROMOTE_MIN) >= 0;
    }

    public static String canonical(String name, String version, String arch, String abi, int resourceMb, String payloadHash) {
        return "name=" + name + "\nversion=" + version + "\narch=" + arch + "\nabi=" + abi
                + "\nresource_mb=" + resourceMb + "\npayload_hash=" + payloadHash + "\n";
    }

    public static String sha256(byte[] payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
            return hex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("sha256", exception);
        }
    }

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String sign(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SIGN_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("hmac", exception);
        }
    }

    public static Map<String, Object> bench(int iterations) {
        byte[] payload = "entry=watch\n".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(payload);
        String signature = sign(canonical("door-watch", "1.0.0", ARCH, ABI, 16, hash));
        long started = System.nanoTime();
        int accepted = 0;
        for (int i = 0; i < iterations; i++) {
            if (inspect("door-watch", "1.0.0", ARCH, ABI, 16, hash, signature, payload).loaded) {
                accepted++;
            }
        }
        long elapsed = (System.nanoTime() - started) / 1000000L;
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("iterations", Integer.valueOf(iterations));
        report.put("accepted", Integer.valueOf(accepted));
        report.put("elapsed_ms", Long.valueOf(elapsed));
        return report;
    }

    private static String flip(String signature) {
        char last = signature.charAt(signature.length() - 1);
        return signature.substring(0, signature.length() - 1) + (last == '0' ? '1' : '0');
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String hex(byte[] raw) {
        StringBuilder builder = new StringBuilder(raw.length * 2);
        for (byte value : raw) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    public static final class Verdict {
        public final boolean signatureOk;
        public final boolean hashChecked;
        public final boolean hashOk;
        public final boolean abiOk;
        public final boolean resourceOk;
        public final boolean loaded;
        public final String reason;
        public final BigDecimal health;

        public Verdict(boolean signatureOk, boolean hashChecked, boolean hashOk, boolean abiOk, boolean resourceOk,
                       boolean loaded, String reason, BigDecimal health) {
            this.signatureOk = signatureOk;
            this.hashChecked = hashChecked;
            this.hashOk = hashOk;
            this.abiOk = abiOk;
            this.resourceOk = resourceOk;
            this.loaded = loaded;
            this.reason = reason;
            this.health = Quantities.persisted(health);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("loaded", Boolean.valueOf(loaded));
            row.put("hash_checked", Boolean.valueOf(hashChecked));
            row.put("reason", reason);
            row.put("health", health.toPlainString());
            row.put("check_order", CHECK_ORDER);
            return row;
        }
    }
}
