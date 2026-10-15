package com.example.ums.admin.web;

import com.example.ums.admin.dto.SourceCreateRequest;
import com.example.ums.admin.dto.SourceUpdateRequest;
import com.example.ums.admin.model.CpSource;
import com.example.ums.admin.service.AdminSourceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** 信源管理接口（ADM-001）。 */
@RestController
@RequestMapping("/api/v1/admin/sources")
public class AdminSourceController {

    private final AdminSourceService adminSourceService;

    public AdminSourceController(AdminSourceService adminSourceService) {
        this.adminSourceService = adminSourceService;
    }

    /** 信源列表。 */
    @GetMapping
    public List<CpSource> list() {
        return adminSourceService.list();
    }

    /** 新增信源。 */
    @PostMapping
    public ResponseEntity<CpSource> create(@RequestBody SourceCreateRequest request) {
        try {
            CpSource created = adminSourceService.create(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** 更新信源可编辑字段（启停/频率/优先级/名称/备注）。 */
    @PutMapping("/{sourceId}")
    public CpSource update(@PathVariable String sourceId, @RequestBody SourceUpdateRequest request) {
        try {
            return adminSourceService.update(sourceId, request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** 手工触发一次采集。 */
    @PostMapping("/{sourceId}/trigger")
    public Map<String, Object> trigger(@PathVariable String sourceId,
                                       @RequestParam(defaultValue = "3") int limit) {
        try {
            String result = adminSourceService.trigger(sourceId, limit);
            return Map.of("sourceId", sourceId, "crawlerResponse", result == null ? "" : result);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
