package com.example.ums.admin;

import com.example.ums.admin.dto.ReviewItemDto;
import com.example.ums.admin.service.impl.AdminReviewServiceImpl;
import com.example.ums.processing.mapper.NewsMapper;
import com.example.ums.processing.model.CpNews;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 审核状态机单元测试（Pending/NeedsReview → Approved | Rejected）。 */
class AdminReviewServiceImplTest {

    private NewsMapper newsMapper;
    private AdminReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        newsMapper = mock(NewsMapper.class);
        service = new AdminReviewServiceImpl(newsMapper, new ObjectMapper());
    }

    private CpNews newsWithStatus(String status) {
        CpNews news = new CpNews();
        news.setArticleId("uuid-1");
        news.setTitle("测试文章");
        news.setManualReviewStatus(status);
        return news;
    }

    @Test
    void approve_shouldMovePendingToApproved() {
        when(newsMapper.selectById("uuid-1")).thenReturn(newsWithStatus("Pending"));

        service.approve("uuid-1", "内容无误");

        ArgumentCaptor<CpNews> captor = ArgumentCaptor.forClass(CpNews.class);
        verify(newsMapper).updateById(captor.capture());
        assertEquals("Approved", captor.getValue().getManualReviewStatus());
        assertEquals("内容无误", captor.getValue().getReviewComment());
    }

    @Test
    void approve_isIdempotentWhenAlreadyApproved() {
        when(newsMapper.selectById("uuid-1")).thenReturn(newsWithStatus("Approved"));

        service.approve("uuid-1", null);

        verify(newsMapper, never()).updateById(any(CpNews.class));
    }

    @Test
    void approve_rejectsTerminalState() {
        when(newsMapper.selectById("uuid-1")).thenReturn(newsWithStatus("Rejected"));

        assertThrows(IllegalStateException.class, () -> service.approve("uuid-1", null));
    }

    @Test
    void approve_unknownArticle_throws() {
        when(newsMapper.selectById("nope")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.approve("nope", null));
    }

    @Test
    void reject_requiresReason() {
        when(newsMapper.selectById("uuid-1")).thenReturn(newsWithStatus("Pending"));

        assertThrows(IllegalArgumentException.class, () -> service.reject("uuid-1", " "));
    }

    @Test
    void reject_shouldMoveNeedsReviewToRejectedWithReason() {
        when(newsMapper.selectById("uuid-1")).thenReturn(newsWithStatus("NeedsReview"));

        service.reject("uuid-1", "内容过期");

        ArgumentCaptor<CpNews> captor = ArgumentCaptor.forClass(CpNews.class);
        verify(newsMapper).updateById(captor.capture());
        assertEquals("Rejected", captor.getValue().getManualReviewStatus());
        assertEquals("内容过期", captor.getValue().getReviewComment());
    }

    @Test
    void updateCategory_overwritesJson() {
        when(newsMapper.selectById("uuid-1")).thenReturn(newsWithStatus("Pending"));

        service.updateCategory("uuid-1", List.of("BESS", "Policy"));

        ArgumentCaptor<CpNews> captor = ArgumentCaptor.forClass(CpNews.class);
        verify(newsMapper).updateById(captor.capture());
        assertTrue(captor.getValue().getCategory().contains("\"BESS\""));
    }

    @Test
    void updateCategory_emptyList_throws() {
        when(newsMapper.selectById("uuid-1")).thenReturn(newsWithStatus("Pending"));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateCategory("uuid-1", List.of()));
    }

    @Test
    void list_mapsEntityToDto() {
        CpNews news = newsWithStatus("Pending");
        news.setSummary("x".repeat(500));
        when(newsMapper.selectList(any())).thenReturn(List.of(news));

        List<ReviewItemDto> result = service.list("Pending", 10, 0);

        assertEquals(1, result.size());
        assertEquals("Pending", result.get(0).reviewStatus());
        assertTrue(result.get(0).summary().endsWith("..."));
        assertEquals(203, result.get(0).summary().length());
    }
}
