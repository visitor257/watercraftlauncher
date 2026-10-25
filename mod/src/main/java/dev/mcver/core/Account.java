package dev.mcver.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** 一个账号：离线（自定名字）或微软正版（带 refresh token）。 */
public final class Account {

    public static final String OFFLINE = "offline";
    public static final String MSA = "msa";

    public String type = OFFLINE;
    public String name = "";
    public String uuid = "";
    public String accessToken = "";
    public String refreshToken = "";
    /** access token 到期时间（epoch millis），0 = 未知。 */
    public long expiresAt;

    public static Account offline(String name) {
        Account a = new Account();
        a.type = OFFLINE;
        a.name = name;
        a.uuid = offlineUuid(name);
        return a;
    }

    /** 原版离线模式的 UUID 算法：UUID v3 of "OfflinePlayer:<name>"。 */
    public static String offlineUuid(String name) {
        return java.util.UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    public boolean isMsa() { return MSA.equals(type); }

    public boolean expired() {
        return isMsa() && expiresAt > 0 && System.currentTimeMillis() > expiresAt - 60_000L;
    }

    public String label() {
        return name + (isMsa() ? "  [正版]" : "  [离线]");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("name", name);
        m.put("uuid", uuid);
        m.put("accessToken", accessToken);
        m.put("refreshToken", refreshToken);
        m.put("expiresAt", expiresAt);
        return m;
    }

    public static Account fromMap(Map<String, Object> m) {
        Account a = new Account();
        a.type = Json.str(m, "type", OFFLINE);
        a.name = Json.str(m, "name", "");
        a.uuid = Json.str(m, "uuid", "");
        a.accessToken = Json.str(m, "accessToken", "");
        a.refreshToken = Json.str(m, "refreshToken", "");
        a.expiresAt = Json.lng(m, "expiresAt", 0);
        if (a.uuid.isEmpty()) a.uuid = offlineUuid(a.name);
        return a;
    }
}
