package com.liushao.service;

import com.liushao.dao.*;
import com.liushao.model.entity.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class BmsTagService {
  private final BmsTagDao tags;
  private final BmsTopicTagDao links;
  private final CommunityData data;

  public BmsTagService(BmsTagDao tags, BmsTopicTagDao links, CommunityData data) {
    this.data = data;
    this.tags = tags;
    this.links = links;
  }

  public List<BmsTag> popular() {
    return data
        .rows(
            "SELECT g.id,g.name,COUNT(*) AS topic_count FROM bms_tag g JOIN bms_post_tag pt ON"
                + " pt.tag_id=g.id JOIN bms_post p ON p.id=pt.topic_id JOIN ums_user u ON"
                + " u.id=p.user_id WHERE "
                + CommunityData.PUBLIC_POST
                + " GROUP BY g.id,g.name ORDER BY topic_count DESC,g.name LIMIT 50")
        .stream()
        .map(
            row -> {
              var tag = new BmsTag();
              tag.setId(CommunityData.id(row, "id"));
              tag.setName((String) row.get("name"));
              tag.setTopicCount(CommunityData.id(row, "topicCount"));
              return tag;
            })
        .toList();
  }

  public List<BmsTag> forPost(int id) {
    return tags.findAllById(links.findByTopicId(id).stream().map(BmsTopicTag::getTagId).toList());
  }

  /** Called within the post transaction; the post row is locked for edits/deletes. */
  public void replace(int postId, List<String> names) {
    List<BmsTopicTag> old = links.findByTopicId(postId);
    links.deleteAll(old);
    links.flush();
    old.stream().map(BmsTopicTag::getTagId).sorted().forEach(id -> tags.adjustCount(id, -1));
    names.stream()
        .map(String::trim)
        .map(name -> name.toLowerCase(Locale.ROOT))
        .distinct()
        .sorted()
        .forEach(
            name -> {
              BmsTag tag = tags.findByName(name);
              if (tag == null) {
                tag = new BmsTag();
                tag.setName(name);
                tag = tags.saveAndFlush(tag);
              }
              var link = new BmsTopicTag();
              link.setTagId(tag.getId());
              link.setTopicId(postId);
              links.save(link);
              tags.adjustCount(tag.getId(), 1);
            });
  }
}
