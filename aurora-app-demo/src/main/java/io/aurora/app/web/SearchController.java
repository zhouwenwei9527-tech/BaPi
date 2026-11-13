package io.aurora.app.web;

import io.aurora.contract.ApiResponse;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.Principal;
import io.aurora.retrieval.RetrievalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Unified retrieval entry for the Command Palette. Text and image queries
 * return ACTION / ENTITY / KNOWLEDGE with ACL filtering applied before recall.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private final RetrievalService retrievalService;

    public SearchController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping
    public ApiResponse<RetrievalService.SearchResult> search(
            @RequestAttribute Principal principal,
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "limit", defaultValue = "8") int limit) {
        return ApiResponse.ok(retrievalService.search(principal, query, null, null, null, limit));
    }

    @PostMapping("/image")
    public ApiResponse<RetrievalService.SearchResult> imageSearch(
            @RequestAttribute Principal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "caption", defaultValue = "") String caption,
            @RequestParam(name = "limit", defaultValue = "8") int limit) {
        if (file == null || file.isEmpty()) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "请上传一张图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "图片不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "只接受图片文件");
        }
        try {
            return ApiResponse.ok(retrievalService.search(principal, query, file.getBytes(),
                    caption, file.getOriginalFilename(), limit));
        } catch (IOException e) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "无法读取上传的图片", e);
        }
    }
}
