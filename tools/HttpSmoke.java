import dev.mcver.core.Net;

// 对本地回环服务做一次真实往返：POST 表单 / POST JSON（带 bearer）/ GET JSON。
public class HttpSmoke {
    public static void main(String[] a) throws Exception {
        Net.Resp r1 = Net.postForm("http://127.0.0.1:18080/form", "a=1&b=%E4%BD%A0%E5%A5%BD", 3000, 5000);
        System.out.println("postForm   status=" + r1.status + " body=" + r1.text);
        Net.Resp r2 = Net.postJson("http://127.0.0.1:18080/json", "{\"hello\":\"world\"}", "TOKEN123", 3000, 5000);
        System.out.println("postJson   status=" + r2.status + " body=" + r2.text);
        Net.Resp r3 = Net.getJson("http://127.0.0.1:18080/get", "BEARER456", 3000, 5000);
        System.out.println("getJson    status=" + r3.status + " body=" + r3.text);
        System.out.println("ok=" + (r1.ok() && r2.ok() && r3.ok()));
    }
}
