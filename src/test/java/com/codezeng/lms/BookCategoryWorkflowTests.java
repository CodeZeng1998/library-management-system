package com.codezeng.lms;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BookCategory;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.service.BookCategoryService;
import com.codezeng.lms.service.BookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookCategoryWorkflowTests {

    @Autowired
    private BookCategoryService categoryService;

    @Autowired
    private BookService bookService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndSearchesCategoryDictionaryItems() {
        categoryService.save(category("Architecture", "Design and engineering"), null);

        assertThat(categoryService.search("arch", org.springframework.data.domain.Pageable.unpaged()))
                .anySatisfy(item -> {
                    assertThat(item.getName()).isEqualTo("Architecture");
                    assertThat(item.getDescription()).isEqualTo("Design and engineering");
                });
    }

    @Test
    void rejectsDuplicateCategoryNameAndSelfParent() {
        BookCategory saved = categoryService.save(category("Operations", ""), null);

        assertThatThrownBy(() -> categoryService.save(category("Operations", "Duplicate"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> categoryService.save(saved, saved.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preventsDeletingCategoryWithBooksOrChildren() {
        BookCategory parent = categoryService.save(category("Computer Science", ""), null);
        categoryService.save(category("Distributed Systems", ""), parent.getId());

        assertThatThrownBy(() -> categoryService.softDelete(parent.getId()))
                .isInstanceOf(IllegalStateException.class);

        BookCategory standalone = categoryService.save(category("Data Quality", ""), null);
        Book book = book("9783000000001");
        book.setCategory(standalone);
        bookService.save(book);

        assertThatThrownBy(() -> categoryService.softDelete(standalone.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deletesUnusedCategory() {
        BookCategory category = categoryService.save(category("Temporary", ""), null);

        categoryService.softDelete(category.getId());

        assertThat(categoryService.search("Temporary", org.springframework.data.domain.Pageable.unpaged())).isEmpty();
    }

    @Test
    void allowsRecreatingCategoryNameAfterSoftDelete() {
        BookCategory category = categoryService.save(category("Seasonal Shelf", ""), null);
        categoryService.softDelete(category.getId());

        BookCategory recreated = categoryService.save(category("Seasonal Shelf", "Recreated dictionary item"), null);

        assertThat(recreated.getId()).isNotEqualTo(category.getId());
        assertThat(recreated.getDescription()).isEqualTo("Recreated dictionary item");
        assertThat(categoryService.search("Seasonal Shelf", org.springframework.data.domain.Pageable.unpaged()))
                .singleElement()
                .extracting(BookCategory::getId)
                .isEqualTo(recreated.getId());
    }

    @Test
    void exportsFilteredCategoriesAsCsv() {
        categoryService.save(category("Exportable Category", "For audit"), null);
        categoryService.save(category("Hidden Category", "Filtered"), null);

        String csv = categoryService.exportCsv("Exportable");

        assertThat(csv).contains("Name,Parent,Description,Book Count,Updated At");
        assertThat(csv).contains("Exportable Category");
        assertThat(csv).doesNotContain("Hidden Category");
    }

    @Test
    @WithMockUser(authorities = "CATEGORY_MANAGE")
    void categoryPageRequiresDictionaryPermission() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "CATEGORY_MANAGE")
    void categoryExportRequiresDictionaryPermission() throws Exception {
        mockMvc.perform(get("/categories/export"))
                .andExpect(status().isOk());
    }

    private BookCategory category(String name, String description) {
        BookCategory category = new BookCategory();
        category.setName(name);
        category.setDescription(description);
        return category;
    }

    private Book book(String isbn) {
        Book book = new Book();
        book.setIsbn(isbn);
        book.setTitle("Category Handbook");
        book.setAuthor("Test Author");
        book.setPublisher("Test Publisher");
        book.setTotalQuantity(1);
        book.setAvailableQuantity(1);
        book.setPrice(new BigDecimal("42.00"));
        book.setLocation("Dictionary Shelf");
        return book;
    }
}
