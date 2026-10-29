package com.liushao.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.liushao.model.entity.BmsPost;
import com.liushao.model.entity.UmsUser;
import jakarta.persistence.EntityManager;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:dao;MODE=MySQL;DB_CLOSE_DELAY=-1"
    })
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.liushao.utils.JwtUtil.class)
class BmsTopicDaoTest {
  @org.springframework.test.context.bean.override.mockito.MockitoBean
  com.liushao.security.CommunitySessions sessions;

  @Autowired BmsTopicDao posts;
  @Autowired EntityManager entityManager;

  @Test
  void nativeQueryMapsJoinedFieldsAndPaginationCount() {
    UmsUser user = new UmsUser();
    user.setUsername("alice");
    user.setAlias("Alice");
    user.setEmail("alice@example.com");
    user.setPassword("test-password");
    entityManager.persist(user);
    BmsPost post = new BmsPost();
    post.setModerationStatus("PUBLISHED");
    post.setTitle("Hello");
    post.setContent("content");
    post.setUserId(user.getId());
    post.setCreateTime(new Date());
    entityManager.persist(post);
    entityManager.flush();
    entityManager.clear();
    var page = posts.findPostList("latest", PageRequest.of(0, 10));
    assertEquals(1, page.getTotalElements());
    assertEquals("Hello", page.getContent().getFirst().getTitle());
    assertEquals(user.getId(), page.getContent().getFirst().getUserId());
    assertEquals("Alice", page.getContent().getFirst().getAlias());
    assertNotNull(page.getContent().getFirst().getCreateTime());
    assertTrue(posts.findPostList("latest", PageRequest.of(1, 10)).isEmpty());
  }
}
