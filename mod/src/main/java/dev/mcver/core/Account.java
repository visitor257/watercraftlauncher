package dev.mcver.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一个账号：离线复用（从已登录过的微软账号派生）或微软正版（带 refresh token）。
 *
 * <p><b>2026-11-01 起的合规约束</b>：不再支持「随便起个名字新建离线账号」——
 * 那等于给没买游戏的人开了条路。现在的离线账号只能是**正版账号的离线副本**：
 * 必须先成功登录过微软账号，然后才能派生一个同名同 UUID 的离线档案，
 * 用于断网时继续玩（和官方启动器的 "Play offline" 一个道理）。
 * 判定见 {@link #offlineOf(Account)} 与 {@code Config.hasMsaFor(uuid)}。
 */
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

    /**
     * 从**已登录的微软账号**派生一个离线副本（同名、同 UUID）。
     *
     * <p>这是现在唯一允许产生离线账号的途径：它明确绑定到一个已经验证过正版身份的账号上。
     */
    public static Account offlineOf(Account msa) {
        if (msa == null || !msa.isMsa()) {
            throw new IllegalArgumentException("离线副本只能从已登录的微软正版账号派生");
        }
        Account a = new Account();
        a.type = OFFLINE;
        a.name = msa.name;
        a.uuid = msa.uuid.isEmpty() ? offlineUuid(msa.name) : msa.uuid;
        return a;
    }

    /** 原版离线模式的 UUID 算法：UUID v3 of "OfflinePlayer:&lt;name&gt;"。仅用于兜底。 */
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
