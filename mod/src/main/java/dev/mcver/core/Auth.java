package dev.mcver.core;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 微软正版登录（设备码流程）+ 离线账号。
 *
 * 流程：device code → 用户去 microsoft.com/link 输码 → 轮询拿 MS token
 *      → Xbox Live → XSTS → Minecraft Services → profile。
 *
 * 不需要浏览器回调、不需要内嵌 WebView，所以很适合塞进游戏里。
 */
public final class Auth {

    /** 官方启动器的公开 client id（legacy Live 应用，支持设备码）。 */
    public static final String CLIENT_ID = "00000000402b5328";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    private static final String DEVICE_URL = "https://login.live.com/oauth20_connect.srf";
    private static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Auth() { }

    public static final class AuthException extends Exception {
        public AuthException(String m) { super(m); }
    }

    /** 设备码：给用户看的短码 + 轮询用的长码。 */
    public static final class Device {
        public String deviceCode = "";
        public String userCode = "";
        public String verifyUri = "https://www.microsoft.com/link";
        public String message = "";
        public int interval = 5;
        public int expiresIn = 900;
    }

    // ================================================================ 微软：第一步

    public static Device startDevice() throws Exception {
        String form = "client_id=" + enc(CLIENT_ID)
                + "&scope=" + enc(SCOPE)
                + "&response_type=" + enc("device_code");
        Map<String, Object> r = postForm(DEVICE_URL, form);
        String err = Json.str(r, "error", null);
        if (err != null) {
            throw new AuthException("申请设备码失败: " + err + " " + Json.str(r, "error_description", ""));
        }
        Device d = new Device();
        d.deviceCode = Json.str(r, "device_code", "");
        d.userCode = Json.str(r, "user_code", "");
        d.verifyUri = Json.str(r, "verification_uri", d.verifyUri);
        d.message = Json.str(r, "message", "");
        d.interval = Math.max(2, Json.num(r, "interval", 5));
        d.expiresIn = Json.num(r, "expires_in", 900);
        if (d.deviceCode.isEmpty() || d.userCode.isEmpty()) {
            throw new AuthException("设备码响应不完整");
        }
        return d;
    }

    // ================================================================ 微软：轮询 + 换 Minecraft token

    /**
     * 轮询直到用户完成授权。onStatus 会在后台线程被调用，用来更新 UI。
     */
    public static Account awaitLogin(Device d, Consumer<String> onStatus, BooleanSupplier cancelled)
            throws Exception {
        long deadline = System.currentTimeMillis() + d.expiresIn * 1000L;
        int interval = d.interval;
        onStatus.accept("等待浏览器授权…");

        while (System.currentTimeMillis() < deadline) {
            if (cancelled != null && cancelled.getAsBoolean()) throw new AuthException("已取消");

            String form = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
                    + "&client_id=" + enc(CLIENT_ID)
                    + "&device_code=" + enc(d.deviceCode);
            Map<String, Object> r = postForm(TOKEN_URL, form);
            String err = Json.str(r, "error", null);

            if (err == null) {
                String msToken = Json.str(r, "access_token", "");
                String refresh = Json.str(r, "refresh_token", "");
                if (msToken.isEmpty()) throw new AuthException("没拿到微软 token");
                onStatus.accept("微软授权完成，正在换取 Xbox Live…");
                return loginWithXbox(msToken, refresh);
            }
            switch (err) {
                case "authorization_pending":
                    break;
                case "slow_down":
                    interval += 5;
                    break;
                case "expired_token":
                    throw new AuthException("设备码已过期，重新登录一次");
                case "authorization_declined":
                    throw new AuthException("你在浏览器里拒绝了授权");
                default:
                    throw new AuthException("登录失败: " + err + " "
                            + Json.str(r, "error_description", ""));
            }
            sleep(interval * 1000L);
        }
        throw new AuthException("等太久没授权，已超时");
    }

    /** 用 refresh token 续期。 */
    public static Account refresh(Account old) throws Exception {
        if (old == null || old.refreshToken.isEmpty()) {
            throw new AuthException("没有 refresh token，请重新登录");
        }
        String form = "grant_type=refresh_token"
                + "&client_id=" + enc(CLIENT_ID)
                + "&refresh_token=" + enc(old.refreshToken)
                + "&scope=" + enc(SCOPE);
        Map<String, Object> r = postForm(TOKEN_URL, form);
        String err = Json.str(r, "error", null);
        if (err != null) {
            throw new AuthException("续期失败: " + err);
        }
        String msToken = Json.str(r, "access_token", "");
        String refresh = Json.str(r, "refresh_token", old.refreshToken);
        Account a = loginWithXbox(msToken, refresh);
        // 名字/UUID 沿用本地（profile 一般也一样）
        return a;
    }

    private static Account loginWithXbox(String msAccess, String refresh) throws Exception {
        // ---- Xbox Live（MSA token 用 t= 前缀；AAD token 用 d=，两个都试一下）
        Map<String, Object> xbl = null;
        String xblToken = "";
        String uhs = "";
        for (String prefix : new String[]{"t=", "d="}) {
            String xblBody = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
                    + "\"RpsTicket\":\"" + prefix + escape(msAccess) + "\"},"
                    + "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
            Map<String, Object> r = postJson(XBL_URL, xblBody, null);
            String t = Json.str(r, "Token", "");
            if (!t.isEmpty()) {
                xbl = r;
                xblToken = t;
                uhs = xblUhs(r);
                break;
            }
        }
        if (xblToken.isEmpty()) throw new AuthException("Xbox Live 认证失败");

        // ---- XSTS
        String xstsBody = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\""
                + escape(xblToken) + "\"]},"
                + "\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
        Map<String, Object> xsts = postJson(XSTS_URL, xstsBody, null);
        String xstsToken = Json.str(xsts, "Token", "");
        if (xstsToken.isEmpty()) {
            Long xerr = xsts.get("XErr") instanceof Number ? ((Number) xsts.get("XErr")).longValue() : null;
            throw new AuthException(xstsMessage(xerr));
        }
        if (uhs.isEmpty()) uhs = xblUhs(xsts);

        // ---- Minecraft Services
        String mcBody = "{\"identityToken\":\"XBL3.0 x=" + escape(uhs) + ";" + escape(xstsToken) + "\"}";
        Map<String, Object> mc = postJson(MC_LOGIN_URL, mcBody, null);
        String mcToken = Json.str(mc, "access_token", "");
        if (mcToken.isEmpty()) {
            throw new AuthException("Minecraft 登录失败: " + Json.str(mc, "errorMessage", "未知错误"));
        }
        long expiresIn = Json.lng(mc, "expires_in", 86400);

        // ---- Profile
        Map<String, Object> prof = getJson(MC_PROFILE_URL, mcToken);
        String name = Json.str(prof, "name", "");
        String id = Json.str(prof, "id", "");
        if (name.isEmpty() || id.isEmpty()) {
            throw new AuthException("这个账号没有 Minecraft 档案（可能没买游戏）");
        }

        Account a = new Account();
        a.type = Account.MSA;
        a.name = name;
        a.uuid = id.replace("-", "");
        a.accessToken = mcToken;
        a.refreshToken = refresh == null ? "" : refresh;
        a.expiresAt = System.currentTimeMillis() + expiresIn * 1000L;
        return a;
    }

    private static String xstsMessage(Long xerr) {
        if (xerr == null) return "XSTS 认证失败（未知原因）";
        long e = xerr;
        if (e == 2148916233L) return "这个微软账号还没有 Xbox 账号，先去 xbox.com 建一个";
        if (e == 2148916235L) return "这个账号所在地区不支持 Xbox Live";
        if (e == 2148916238L) return "未成年账号需要家长先在 Xbox 家庭里批准";
        if (e == 2148916227L) return "这个账号被封禁了";
        return "XSTS 认证失败: XErr=" + xerr;
    }

    private static String xblUhs(Map<String, Object> r) {
        try {
            Map<String, Object> claims = Json.obj(r.get("DisplayClaims"));
            List<Object> xui = Json.arr(claims.get("xui"));
            if (!xui.isEmpty()) return Json.str(Json.obj(xui.get(0)), "uhs", "");
        } catch (Exception ignored) { }
        return "";
    }

    // ================================================================ HTTP

    private static Map<String, Object> postForm(String url, String form) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        return send(req);
    }

    private static Map<String, Object> postJson(String url, String body, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        return send(b.build());
    }

    private static Map<String, Object> getJson(String url, String bearer) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearer)
                .GET()
                .build();
        return send(req);
    }

    private static Map<String, Object> send(HttpRequest req) throws Exception {
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> m;
        try {
            m = new LinkedHashMap<>(Json.obj(Json.parse(r.body())));
        } catch (Exception e) {
            m = new LinkedHashMap<>();
        }
        m.put("__status", r.statusCode());
        return m;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
