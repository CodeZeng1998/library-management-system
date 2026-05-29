package com.codezeng.lms;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BookTag;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.service.BookService;
import com.codezeng.lms.service.BookTagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookTagWorkflowTests {

    @Autowired
    private BookTagService tagService;

    @Autowired
    private BookService bookService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndSearchesTagDictionaryItems() {
        tagService.save(tag("AI", "Artificial intelligence"));

        assertThat(tagService.search("ai", org.springframework.data.domain.Pageable.unpaged()))
                .anySatisfy(item -> {
                    assertThat(item.getName()).isEqualTo("AI");
                    assertThat(item.getDescription()).isEqualTo("Artificial intelligence");
                });
    }

    @Test
    void rejectsDuplicateTagNameIgnoringCase() {
        tagService.save(tag("Operations", ""));

        assertThatThrownBy(() -> tagService.save(tag("operations", "Duplicate")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preventsDeletingTagUsedByBooks() {
        BookTag tag = tagService.save(tag("Staff Pick", ""));
        Book book = book("9784000000001");
        book.setTags(Set.of(tag));
        bookService.save(book);

        assertThatThrownBy(() -> tagService.softDelete(tag.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deletesUnusedTag() {
        BookTag tag = tagService.save(tag("Temporary", ""));

        tagService.softDelete(tag.getId());

        assertThat(tagService.search("Temporary", org.springframework.data.domain.Pageable.unpaged())).isEmpty();
    }

    @Test
    void allowsRecreatingTagNameAfterSoftDelete() {
        BookTag tag = tagService.save(tag("Seasonal", ""));
        tagService.softDelete(tag.getId());

        BookTag recreated = tagService.save(tag("Seasonal", "Recreated dictionary item"));

        assertThat(recreated.getId()).isNotEqualTo(tag.getId());
        assertThat(recreated.getDescription()).isEqualTo("Recreated dictionary item");
        assertThat(tagService.search("Seasonal", org.springframework.data.domain.Pageable.unpaged()))
                .singleElement()
                .extracting(BookTag::getId)
                .isEqualTo(recreated.getId());
    }

    @Test
    @WithMockUser(authorities = "TAG_MANAGE")
    void tagPageRequiresDictionaryPermission() throws Exception {
        mockMvc.perform(get("/tags"))
                .andExpect(status().isOk());
    }

    private BookTag tag(String name, String description) {
        BookTag tag = new BookTag();
        tag.setName(name);
        tag.setDescription(description);
        return tag;
    }

    private Book book(String isbn) {
        Book book = new Book();
        book.setIsbn(isbn);
        book.setTitle("Tag Handbook");
        book.setAuthor("Test Author");
        book.setPublisher("Test Publisher");
        book.setTotalQuantity(1);
        book.setAvailableQuantity(1);
        book.setPrice(new BigDecimal("42.00"));
        book.setLocation("Tag Shelf");
        return book;
    }
}
