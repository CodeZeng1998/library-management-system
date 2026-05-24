package com.codezeng.lms.web;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.security.DataScopeService;
import com.codezeng.lms.service.RecommendationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
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

    public RecommendationController(RecommendationService recommendationService,
                                    ReaderRepository readerRepository,
                                    BookRepository bookRepository,
                                    DataScopeService dataScopeService) {
        this.recommendationService = recommendationService;
        this.readerRepository = readerRepository;
        this.bookRepository = bookRepository;
        this.dataScopeService = dataScopeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RECOMMENDATION_VIEW')")
    public String index(@RequestParam(required = false) Long readerId,
                        @RequestParam(required = false) Long bookId,
                        Model model) {
        model.addAttribute("readers", readerRepository.findByDeletedFalse(PageRequest.of(0, 200, Sort.by("readerNo"))).getContent());
        model.addAttribute("books", visibleBooks());
        model.addAttribute("dashboard", recommendationService.dashboard(readerId, bookId));
        model.addAttribute("readerId", readerId);
        model.addAttribute("bookId", bookId);
        return "recommendation/index";
    }

    private List<Book> visibleBooks() {
        return bookRepository.findAll(dataScopeService.bookScope(), Sort.by("title"));
    }
}
