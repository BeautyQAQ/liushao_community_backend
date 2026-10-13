package com.liushao.service;

import com.liushao.dao.*;
import com.liushao.model.entity.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class BmsTagService {
  private final BmsTagDao tags;
  private final BmsTopicTagDao links;

  public BmsTagService(BmsTagDao tags, BmsTopicTagDao links) {
    this.tags = tags;
    this.links = links;
  }

  public List<BmsTag> popular() {
    return tags.findTop50ByTopicCountGreaterThanOrderByTopicCountDescNameAsc(0);
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
