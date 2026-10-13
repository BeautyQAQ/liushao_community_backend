package com.liushao.service;

import com.liushao.common.exception.ApiException;
import com.liushao.dao.*;
import com.liushao.model.dto.CreateTopicDTO;
import com.liushao.model.entity.*;
import com.liushao.model.vo.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BmsPostService {
  private final BmsTopicDao posts;
  private final UmsUserDao users;
  private final BmsTagService tags;
  private final BmsTopicTagDao links;
  private final BmsTagDao tagDao;

  public BmsPostService(
      BmsTopicDao posts,
      UmsUserDao users,
      BmsTagService tags,
      BmsTopicTagDao links,
      BmsTagDao tagDao) {
    this.posts = posts;
    this.users = users;
    this.tags = tags;
    this.links = links;
    this.tagDao = tagDao;
  }

  public Page<PostVO> getList(String tab, int page, int size) {
    return getList(tab, page, size, "", "", "");
  }

  @Transactional(readOnly = true)
  public Page<PostVO> getList(
      String tab, int page, int size, String query, String tag, String author) {
    if (!Set.of("latest", "hot").contains(tab)
        || page < 1
        || size < 1
        || size > 50
        || (long) (page - 1) * size > Integer.MAX_VALUE
        || query.length() > 100
        || tag.length() > 32
        || author.length() > 32) throw new ApiException(400, "分页或搜索参数不正确");
    var result =
        posts.findPostList(
            tab, PageRequest.of(page - 1, size), query.trim(), tag.trim(), author.trim());
    var relations = links.findByTopicIdIn(result.stream().map(PostVO::getId).toList());
    var byId =
        tagDao
            .findAllById(relations.stream().map(BmsTopicTag::getTagId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(BmsTag::getId, t -> t));
    result.forEach(
        p ->
            p.setTags(
                relations.stream()
                    .filter(link -> link.getTopicId().equals(p.getId()))
                    .map(link -> byId.get(link.getTagId()))
                    .toList()));
    return result;
  }

  @Transactional
  public PostDetail detail(int id) {
    var post = posts.findById(id).orElseThrow(() -> new ApiException(404, "帖子不存在"));
    requireAuthor(post);
    posts.incrementViews(id);
    return view(posts.findById(id).orElseThrow());
  }

  @Transactional
  public PostDetail add(CreateTopicDTO dto, UmsUser user) {
    var post = new BmsPost();
    post.setUserId(user.getId());
    post.setTitle(dto.getTitle().trim());
    post.setContent(dto.getContent());
    post.setCreateTime(new Date());
    post.setModifyTime(new Date());
    post = posts.saveAndFlush(post);
    tags.replace(post.getId(), dto.getTags());
    return view(post);
  }

  @Transactional
  public PostDetail update(int id, CreateTopicDTO dto, UmsUser user) {
    var post = owned(id, user);
    if (dto.getVersion() == null) throw new ApiException(400, "缺少帖子版本，请刷新后重试");
    if (!dto.getVersion().equals(post.getVersion())) throw new ApiException(409, "帖子已被修改，请刷新后重试");
    post.setTitle(dto.getTitle().trim());
    post.setContent(dto.getContent());
    post.setModifyTime(new Date());
    posts.saveAndFlush(post);
    tags.replace(id, dto.getTags());
    return view(post);
  }

  @Transactional
  public void delete(int id, UmsUser user) {
    var post = owned(id, user);
    tags.replace(id, List.of());
    posts.delete(post);
  }

  private BmsPost owned(int id, UmsUser user) {
    var post = posts.findLockedById(id).orElseThrow(() -> new ApiException(404, "帖子不存在"));
    if (!post.getUserId().equals(user.getId())) throw new ApiException(403, "只能修改或删除自己的帖子");
    return post;
  }

  private UmsUser requireAuthor(BmsPost post) {
    var user = users.findById(post.getUserId()).orElseThrow(() -> new ApiException(404, "作者不存在"));
    if (!Boolean.TRUE.equals(user.getStatus())) throw new ApiException(404, "帖子不存在");
    return user;
  }

  private PostDetail view(BmsPost post) {
    var user = requireAuthor(post);
    return new PostDetail(
        post.getId(),
        user.getId(),
        user.getUsername(),
        user.getAlias(),
        user.getAvatar(),
        post.getTitle(),
        post.getContent(),
        post.getView(),
        post.getVersion(),
        post.getCreateTime(),
        post.getModifyTime(),
        tags.forPost(post.getId()));
  }
}
