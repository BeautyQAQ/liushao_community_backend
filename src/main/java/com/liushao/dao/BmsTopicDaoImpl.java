package com.liushao.dao;

import com.liushao.model.vo.PostVO;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;

public class BmsTopicDaoImpl {
  @PersistenceContext private EntityManager entityManager;

  public Page<PostVO> findPostList(String tab, Pageable pageable) {
    return findPostList(tab, pageable, "", "", "");
  }

  public Page<PostVO> findPostList(
      String tab, Pageable pageable, String query, String tag, String author) {
    String from = " FROM bms_post t JOIN ums_user u ON t.user_id=u.id WHERE u.status=TRUE";
    Map<String, Object> params = new HashMap<>();
    if ("hot".equals(tab)) {
      from += " AND t.create_time>=:cutoff";
      params.put("cutoff", Date.from(Instant.now().minus(Duration.ofDays(7))));
    }
    if (!query.isBlank()) {
      from +=
          " AND (LOWER(t.title) LIKE :query ESCAPE '!' OR LOWER(t.content) LIKE :query ESCAPE '!'"
              + " OR LOWER(u.username) LIKE :query ESCAPE '!' OR LOWER(u.alias) LIKE :query ESCAPE"
              + " '!' OR EXISTS(SELECT 1 FROM bms_post_tag pt JOIN bms_tag g ON g.id=pt.tag_id"
              + " WHERE pt.topic_id=t.id AND LOWER(g.name) LIKE :query ESCAPE '!'))";
      params.put(
          "query",
          "%"
              + query
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%");
    }
    if (!tag.isBlank()) {
      from +=
          " AND EXISTS(SELECT 1 FROM bms_post_tag pt JOIN bms_tag g ON g.id=pt.tag_id WHERE"
              + " pt.topic_id=t.id AND LOWER(g.name)=:tag)";
      params.put("tag", tag.toLowerCase(Locale.ROOT));
    }
    if (!author.isBlank()) {
      from += " AND u.username=:author";
      params.put("author", author.toLowerCase(Locale.ROOT));
    }
    String sort =
        "hot".equals(tab)
            ? " ORDER BY t.view DESC,t.create_time DESC,t.id DESC"
            : " ORDER BY t.top DESC,t.create_time DESC,t.id DESC";
    var data =
        entityManager
            .unwrap(org.hibernate.Session.class)
            .createNativeQuery(
                "SELECT"
                    + " t.id,t.title,t.user_id,t.comments,t.view,t.collects,t.top,t.essence,t.create_time,t.modify_time,u.username,u.alias,u.avatar"
                    + from
                    + sort,
                Object[].class);
    var count = entityManager.createNativeQuery("SELECT COUNT(*)" + from);
    params.forEach(
        (key, value) -> {
          data.setParameter(key, value);
          count.setParameter(key, value);
        });
    data.setFirstResult(Math.toIntExact(pageable.getOffset()));
    data.setMaxResults(pageable.getPageSize());
    List<PostVO> rows =
        data.getResultList().stream()
            .map(
                row -> {
                  var p = new PostVO();
                  p.setId(integer(row[0]));
                  p.setTitle((String) row[1]);
                  p.setUserId(integer(row[2]));
                  p.setComments(integer(row[3]));
                  p.setView(integer(row[4]));
                  p.setCollects(integer(row[5]));
                  p.setTop(bool(row[6]));
                  p.setEssence(bool(row[7]));
                  p.setCreateTime((Date) row[8]);
                  p.setModifyTime((Date) row[9]);
                  p.setUsername((String) row[10]);
                  p.setAlias((String) row[11]);
                  p.setAvatar((String) row[12]);
                  p.setTags(List.of());
                  return p;
                })
            .toList();
    return new PageImpl<>(rows, pageable, ((Number) count.getSingleResult()).longValue());
  }

  private static Integer integer(Object value) {
    return ((Number) value).intValue();
  }

  private static Boolean bool(Object value) {
    return value instanceof Boolean b ? b : ((Number) value).intValue() != 0;
  }
}
