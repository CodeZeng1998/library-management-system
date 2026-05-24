package com.codezeng.lms.web;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.security.DataScopeService;
import com.codezeng.lms.service.ReaderPortalService;
import com.codezeng.lms.service.RecommendationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final ReaderRepository readerRepository;
    private final BookRepository bookRepository;
    private final DataScopeService dataScopeService;
    private final ReaderPortalService readerPortalService;

    public RecommendationController(RecommendationService recommendationService,
                                    ReaderRepository readerRepository,
                                    BookRepository bookRepository,
                                    DataScopeService dataScopeService,
                                    ReaderPortalService readerPortalService) {
        this.recommendationService = recommendationService;
        this.readerRepository = readerRepository;
        this.bookRepository = bookRepository;
        this.dataScopeService = dataScopeService;
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
            model.addAttribute("readers", readerRepository.findByDeletedFalse(PageRequest.of(0, 200, Sort.by("readerNo"))).getContent());
            model.addAttribute("readerOnly", false);
        }
        model.addAttribute("books", visibleBooks());
        model.addAttribute("dashboard", recommendationService.dashboard(readerId, bookId));
        model.addAttribute("readerId", readerId);
        model.addAttribute("bookId", bookId);
        return "recommendation/index";
    }

    private List<Book> visibleBooks() {
        return bookRepository.findAll(dataScopeService.bookScope(), Sort.by("title"));
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
