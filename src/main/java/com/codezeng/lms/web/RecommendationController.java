package com.codezeng.lms.web;

import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.service.ReaderPortalService;
import com.codezeng.lms.service.RecommendationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final ReaderPortalService readerPortalService;

    public RecommendationController(RecommendationService recommendationService,
                                    ReaderPortalService readerPortalService) {
        this.recommendationService = recommendationService;
        this.readerPortalService = readerPortalService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RECOMMENDATION_VIEW')")
    public String index(@RequestParam(required = false) Long readerId,
                        @RequestParam(required = false) Long bookId,
                        Authentication authentication,
                        Model model) {
        if (isReaderOnly(authentication)) {
            Reader reader = readerPortalService.requireCurrentReader();
            readerId = reader.getId();
            model.addAttribute("readers", List.of(reader));
            model.addAttribute("readerOnly", true);
        } else {
            model.addAttribute("readers", recommendationService.selectableReaders());
            model.addAttribute("readerOnly", false);
        }
        model.addAttribute("books", recommendationService.visibleBooks());
        model.addAttribute("dashboard", recommendationService.dashboard(readerId, bookId));
        model.addAttribute("readerId", readerId);
        model.addAttribute("bookId", bookId);
        return "recommendation/index";
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('RECOMMENDATION_VIEW')")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) Long readerId,
                                            @RequestParam(required = false) Long bookId,
                                            Authentication authentication) {
        if (isReaderOnly(authentication)) {
            readerId = readerPortalService.requireCurrentReader().getId();
        }
        String csv = recommendationService.exportCsv(readerId, bookId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=recommendations.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isReaderOnly(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        boolean reader = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_READER".equals(authority.getAuthority()));
        boolean staff = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority())
                        || "ROLE_LIBRARIAN".equals(authority.getAuthority()));
        return reader && !staff;
    }
}
