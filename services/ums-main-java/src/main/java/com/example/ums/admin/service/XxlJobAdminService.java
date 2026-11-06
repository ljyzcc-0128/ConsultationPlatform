package com.example.ums.admin.service;

import com.example.ums.admin.dto.XxlJobInfo;
import com.example.ums.admin.dto.XxlJobPage;
import com.example.ums.admin.dto.XxlJobUpsertRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * XXL-JOB 调度中心管理接口代理：登录 xxl-job-admin 后转发
 * 任务列表 / 启动 / 停止 / 执行一次（/jobinfo/*），
 * 供管理后台「定时任务维护列表」读取全部定时任务。
 *
 * <p>登录采用 xxl-job-admin 表单登录（userName/password），
 * 会话以 Cookie XXL_JOB_LOGIN_IDENTITY 维持，缓存复用、失效自动重登。
 * 未配置 admin-addresses 时接口返回 503（本地未部署调度中心）。
 */
@Service
public class XxlJobAdminService {

    private static final Logger log = LoggerFactory.getLogger(XxlJobAdminService.class);

    private final String adminAddresses;
    private final String username;
    private final String password;
    /** 执行器 appname（与 XxlJobConfig 一致），用于动态解析执行器分组 ID */
    private final String appname;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** xxl-job-admin 登录会话 Cookie（XXL_JOB_LOGIN_IDENTITY=...），失效后自动重登 */
    private volatile String loginCookie;

    /** 执行器分组 ID（按 appname 从调度中心解析），解析成功后缓存 */
    private volatile Integer jobGroupId;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public XxlJobAdminService(
            @Value("${ums.xxl-job.admin-addresses:}") String adminAddresses,
            @Value("${ums.xxl-job.username:}") String username,
            @Value("${ums.xxl-job.password:}") String password,
            @Value("${ums.xxl-job.appname:}") String appname) {
        this.adminAddresses = adminAddresses == null ? "" : adminAddresses.trim();
        this.username = username;
        this.password = password;
        this.appname = appname == null ? "" : appname.trim();
    }

    /** 任务分页列表（jobDesc 模糊、triggerStatus -1=全部/0=停止/1=运行）。 */
    public XxlJobPage pageList(int start, int length, String jobDesc, Integer triggerStatus) {
        requireConfigured();
        String form = new StringBuilder()
                .append("start=").append(Math.max(0, start))
                .append("&length=").append(Math.min(Math.max(1, length), 100))
                .append("&jobGroup=0")
                .append("&triggerStatus=").append(triggerStatus == null ? -1 : triggerStatus)
                .append("&jobDesc=").append(urlEncode(orEmpty(jobDesc)))
                .toString();

        JsonNode resp = postForJson("/jobinfo/pageList", form);
        XxlJobPage page = new XxlJobPage();
        page.setTotal(resp.path("recordsTotal").asLong(0));
        JsonNode data = resp.path("data");
        List<XxlJobInfo> list = new ArrayList<>();
        if (data.isArray()) {
            data.forEach(item -> list.add(toJobInfo(item)));
        }
        page.setList(list);
        return page;
    }

    /** 启动任务。 */
    public void start(int id) {
        requireConfigured();
        postForJson("/jobinfo/start", "id=" + id);
    }

    /** 停止任务。 */
    public void stop(int id) {
        requireConfigured();
        postForJson("/jobinfo/stop", "id=" + id);
    }

    /** 执行一次（executorParam 覆盖任务配置参数，可为空）。 */
    public void trigger(int id, String executorParam) {
        requireConfigured();
        String form = "id=" + id
                + "&executorParam=" + urlEncode(orEmpty(executorParam))
                + "&addressList=";
        postForJson("/jobinfo/trigger", form);
    }

    /** 新增任务，返回新任务 ID。 */
    public int add(XxlJobUpsertRequest req) {
        requireConfigured();
        JsonNode resp = postForJson("/jobinfo/add", upsertForm(req, null));
        return resp.path("content").asInt(0);
    }

    /** 编辑任务。 */
    public void update(int id, XxlJobUpsertRequest req) {
        requireConfigured();
        postForJson("/jobinfo/update", upsertForm(req, id));
    }

    /** 删除任务。 */
    public void remove(int id) {
        requireConfigured();
        postForJson("/jobinfo/remove", "id=" + id);
    }

    /** 新增/编辑表单（add/update 共用全量字段；update 追加 id）。 */
    private String upsertForm(XxlJobUpsertRequest req, Integer id) {
        requireField(req.getJobDesc(), "任务描述");
        requireField(req.getScheduleConf(), "调度配置");
        requireField(req.getExecutorHandler(), "JobHandler");
        StringBuilder sb = new StringBuilder();
        if (id != null) {
            sb.append("id=").append(id).append('&');
        }
        sb.append("jobGroup=").append(resolveJobGroupId())
                .append("&jobDesc=").append(urlEncode(req.getJobDesc()))
                .append("&scheduleType=").append(urlEncode(orEmpty(req.getScheduleType())))
                .append("&scheduleConf=").append(urlEncode(req.getScheduleConf()))
                .append("&misfireStrategy=").append(urlEncode(orEmpty(req.getMisfireStrategy())))
                .append("&executorRouteStrategy=").append(urlEncode(orEmpty(req.getExecutorRouteStrategy())))
                .append("&executorBlockStrategy=").append(urlEncode(orEmpty(req.getExecutorBlockStrategy())))
                .append("&executorHandler=").append(urlEncode(req.getExecutorHandler()))
                .append("&executorParam=").append(urlEncode(orEmpty(req.getExecutorParam())))
                .append("&childJobId=")
                .append("&alarmEmail=").append(urlEncode(orEmpty(req.getAlarmEmail())))
                .append("&author=").append(urlEncode(orEmpty(req.getAuthor())))
                .append("&glueType=").append(GLUE_TYPE_BEAN);
        return sb.toString();
    }

    /** GLUE 模式固定为 BEAN（Java 任务），属 xxl-job 协议取值而非环境配置 */
    private static final String GLUE_TYPE_BEAN = "BEAN";

    /**
     * 解析本服务执行器对应的分组 ID：按配置的 appname 查询 /jobgroup/pageList，
     * 结果缓存。pageList 的 jobGroup=0 表示「全部分组」（xxl-job 约定），仅查询场景使用。
     */
    private int resolveJobGroupId() {
        Integer cached = jobGroupId;
        if (cached != null) {
            return cached;
        }
        if (appname.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "未配置执行器 appname（ums.xxl-job.appname），无法关联调度中心执行器分组");
        }
        JsonNode resp = postForJson("/jobgroup/pageList",
                "start=0&length=100&appname=" + urlEncode(appname) + "&title=");
        JsonNode data = resp.path("data");
        if (data.isArray()) {
            for (JsonNode group : data) {
                if (appname.equals(group.path("appname").asText())) {
                    int id = group.path("id").asInt();
                    jobGroupId = id;
                    log.info("XXL-JOB 执行器分组已解析: appname={} -> jobGroup={}", appname, id);
                    return id;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "调度中心不存在 appname 为 " + appname + " 的执行器分组，请先在调度中心注册执行器");
    }

    private void requireField(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }

    // ------------------------------------------------------------------
    // 内部：登录会话 + 表单转发
    // ------------------------------------------------------------------

    /** POST 表单到 xxl-job-admin，返回 JSON；会话失效自动重登一次后重试。 */
    private JsonNode postForJson(String path, String form) {
        String body = postForm(path, form);
        JsonNode json = parseOrRelogin(body, path, form);
        // ReturnT 类响应（login/start/stop/trigger）带 code 字段；
        // pageList 类响应为 {"recordsTotal":..,"data":[..]}，无 code 字段，直接透传
        if (json.has("code") && json.path("code").asInt(-1) != 200) {
            String msg = json.path("msg").asText("xxl-job-admin 调用失败");
            log.warn("XXL-JOB admin {} 失败: code={}, msg={}", path, json.path("code").asInt(), msg);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "调度中心返回错误：" + msg);
        }
        return json;
    }

    /** 页面列表接口未登录时返回登录页 HTML 而非 JSON：解析失败则重登一次重试。 */
    private JsonNode parseOrRelogin(String body, String path, String form) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception first) {
            log.info("XXL-JOB admin {} 响应非 JSON（登录态失效），重新登录后重试", path);
            loginCookie = null;
            login();
            String retryBody = postForm(path, form);
            try {
                return objectMapper.readTree(retryBody);
            } catch (Exception second) {
                log.error("XXL-JOB admin {} 重试后响应仍非 JSON: {}", path, brief(retryBody));
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "调度中心响应异常（可能未登录或地址有误）");
            }
        }
    }

    private String postForm(String path, String form) {
        if (loginCookie == null) {
            login();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(adminAddresses + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Cookie", loginCookie)
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.body() == null ? "" : response.body();
        } catch (Exception e) {
            log.error("XXL-JOB admin {} 调用异常: {}", path, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "调度中心不可达，请检查 ums.xxl-job.admin-addresses 配置");
        }
    }

    /** 表单登录 xxl-job-admin，缓存登录 Cookie。 */
    private synchronized void login() {
        if (loginCookie != null) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(adminAddresses + "/login"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "userName=" + urlEncode(username) + "&password=" + urlEncode(password),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode json = objectMapper.readTree(response.body() == null ? "" : response.body());
            if (json.path("code").asInt(-1) != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "调度中心登录失败（检查 ums.xxl-job.username/password）");
            }
            String cookie = response.headers().allValues("set-cookie").stream()
                    .filter(c -> c.startsWith("XXL_JOB_LOGIN_IDENTITY="))
                    .findFirst()
                    .map(c -> c.split(";", 2)[0])
                    .orElse(null);
            if (cookie == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "调度中心登录未返回会话 Cookie");
            }
            loginCookie = cookie;
            log.info("XXL-JOB admin 登录成功: {}", adminAddresses);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("XXL-JOB admin 登录异常: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "调度中心不可达，请检查 ums.xxl-job.admin-addresses 配置");
        }
    }

    private XxlJobInfo toJobInfo(JsonNode item) {
        XxlJobInfo info = new XxlJobInfo();
        info.setId(item.path("id").asInt());
        info.setJobGroup(item.path("jobGroup").asInt());
        info.setJobDesc(item.path("jobDesc").asText());
        info.setScheduleType(item.path("scheduleType").asText());
        info.setScheduleConf(item.path("scheduleConf").asText());
        info.setExecutorRouteStrategy(item.path("executorRouteStrategy").asText());
        info.setExecutorHandler(item.path("executorHandler").asText());
        info.setExecutorParam(item.path("executorParam").asText());
        info.setMisfireStrategy(item.path("misfireStrategy").asText());
        info.setExecutorBlockStrategy(item.path("executorBlockStrategy").asText());
        info.setAuthor(item.path("author").asText());
        info.setAlarmEmail(item.path("alarmEmail").asText());
        info.setTriggerStatus(item.path("triggerStatus").asInt(0));
        info.setTriggerLastTime(item.path("triggerLastTime").asLong(0));
        info.setTriggerNextTime(item.path("triggerNextTime").asLong(0));
        info.setCreateTime(item.path("createTime").asLong(0));
        info.setUpdateTime(item.path("updateTime").asLong(0));
        return info;
    }

    private void requireConfigured() {
        if (adminAddresses.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "未配置调度中心地址（ums.xxl-job.admin-addresses），无法读取定时任务");
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String brief(String body) {
        return body == null ? "" : body.substring(0, Math.min(body.length(), 200));
    }
}
