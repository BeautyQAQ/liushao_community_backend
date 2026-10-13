package com.liushao.dao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageRequest;
import com.liushao.model.entity.BmsPost;
import com.liushao.model.entity.UmsUser;
import jakarta.persistence.EntityManager;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.liushao.utils.JwtUtil.class)
class BmsTopicDaoTest {
    @Autowired BmsTopicDao posts;
    @Autowired EntityManager entityManager;
    @Test void nativeQueryMapsJoinedFieldsAndPaginationCount() {
        UmsUser user = new UmsUser(); user.setUsername("alice"); user.setAlias("Alice");
        entityManager.persist(user);
        BmsPost post = new BmsPost(); post.setTitle("Hello"); post.setContent("content");
        post.setUserId(user.getId()); post.setCreateTime(new Date()); entityManager.persist(post);
        entityManager.flush(); entityManager.clear();
        var page = posts.findPostList("latest", PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("Hello", page.getContent().getFirst().getTitle());
        assertEquals(user.getId(), page.getContent().getFirst().getUserId());
        assertEquals("Alice", page.getContent().getFirst().getAlias());
        assertNotNull(page.getContent().getFirst().getCreateTime());
        assertTrue(posts.findPostList("latest", PageRequest.of(1, 10)).isEmpty());
    }
}
