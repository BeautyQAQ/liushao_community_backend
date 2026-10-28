-- Run by migrate_legacy.py on a connection to the upgraded shared database.
-- ${legacy_schema} is a disposable schema containing the historical dump.
-- ID allocation and the import share one transaction. Existing mapped rows are not overwritten.

INSERT INTO ums_user
 (id,username,alias,password,avatar,email,mobile,bio,score,token,active,status,role_id,
  auth_version,create_time,modify_time)
SELECT ids.current_id,LOWER(TRIM(u.username)),COALESCE(NULLIF(TRIM(u.alias),''),u.username),
 u.password,u.avatar,
 CASE WHEN u.email IS NOT NULL AND CHAR_LENGTH(TRIM(u.email)) BETWEEN 3 AND 128
           AND TRIM(u.email) REGEXP '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'
           AND u.email_rank=1
           AND NOT EXISTS(SELECT 1 FROM ums_user existing WHERE existing.email=LOWER(TRIM(u.email)))
      THEN LOWER(TRIM(u.email))
      ELSE CONCAT('legacy-',u.id,'@example.invalid') END,
 u.mobile,COALESCE(u.bio,''),u.score,NULL,FALSE,COALESCE(u.status,TRUE),u.role_id,1,
 CONVERT_TZ(u.create_time,@legacy_source_time_zone,'+00:00'),
 CONVERT_TZ(COALESCE(u.modify_time,u.create_time),@legacy_source_time_zone,'+00:00')
FROM (SELECT original.*,
       ROW_NUMBER() OVER(PARTITION BY LOWER(TRIM(email)) ORDER BY id) AS email_rank
      FROM ${legacy_schema}.ums_user original) u
JOIN legacy_new_ids ids ON ids.resource='ums_user' AND ids.legacy_id=u.id;

INSERT INTO bms_tag(id,name,topic_count)
SELECT ids.current_id,LOWER(TRIM(t.name)),0
FROM ${legacy_schema}.bms_tag t
JOIN legacy_new_ids ids ON ids.resource='bms_tag' AND ids.legacy_id=t.id;

INSERT INTO bms_post
 (id,title,content,user_id,comments,collects,likes,view,section_id,top,essence,version,
  moderation_status,create_time,modify_time)
SELECT ids.current_id,p.title,COALESCE(p.content,''),author.current_id,0,0,0,
 p.view,COALESCE(p.section_id,0),p.top,p.essence,0,'PUBLISHED',
 CONVERT_TZ(p.create_time,@legacy_source_time_zone,'+00:00'),
 CONVERT_TZ(COALESCE(p.modify_time,p.create_time),@legacy_source_time_zone,'+00:00')
FROM ${legacy_schema}.bms_post p
JOIN legacy_new_ids ids ON ids.resource='bms_post' AND ids.legacy_id=p.id
JOIN legacy_community_id_map author ON author.resource='ums_user' AND author.legacy_id=p.user_id;

INSERT INTO bms_post_tag(id,tag_id,topic_id)
SELECT ids.current_id,tag.current_id,post.current_id
FROM ${legacy_schema}.bms_post_tag link
JOIN legacy_new_ids ids ON ids.resource='bms_post_tag' AND ids.legacy_id=CAST(link.id AS CHAR)
JOIN legacy_community_id_map tag ON tag.resource='bms_tag' AND tag.legacy_id=link.tag_id
JOIN legacy_community_id_map post ON post.resource='bms_post' AND post.legacy_id=link.topic_id;

INSERT INTO bms_comment(id,post_id,user_id,content,created_at)
SELECT ids.current_id,post.current_id,author.current_id,c.content,
 CONVERT_TZ(c.create_time,@legacy_source_time_zone,'+00:00')
FROM ${legacy_schema}.bms_comment c
JOIN legacy_new_ids ids ON ids.resource='bms_comment' AND ids.legacy_id=c.id
JOIN legacy_community_id_map post ON post.resource='bms_post' AND post.legacy_id=c.topic_id
JOIN legacy_community_id_map author ON author.resource='ums_user' AND author.legacy_id=c.user_id;

INSERT INTO ums_follow(follower_id,followed_id)
SELECT DISTINCT follower.current_id,followed.current_id
FROM ${legacy_schema}.bms_follow f
JOIN legacy_community_id_map follower ON follower.resource='ums_user' AND follower.legacy_id=f.follower_id
JOIN legacy_community_id_map followed ON followed.resource='ums_user' AND followed.legacy_id=f.parent_id
WHERE follower.current_id<>followed.current_id
 AND NOT EXISTS(SELECT 1 FROM ums_follow existing
                WHERE existing.follower_id=follower.current_id AND existing.followed_id=followed.current_id);

INSERT INTO bms_billboard(id,content,create_time,is_visible)
SELECT ids.current_id,b.content,CONVERT_TZ(b.create_time,@legacy_source_time_zone,'+00:00'),b.`show`
FROM ${legacy_schema}.bms_billboard b
JOIN legacy_new_ids ids ON ids.resource='bms_billboard' AND ids.legacy_id=CAST(b.id AS CHAR);

INSERT INTO bms_tip(id,content,author,type)
SELECT ids.current_id,t.content,t.author,t.type
FROM ${legacy_schema}.bms_tip t
JOIN legacy_new_ids ids ON ids.resource='bms_tip' AND ids.legacy_id=CAST(t.id AS CHAR);

INSERT INTO bms_promotion(id,title,link,description)
SELECT ids.current_id,p.title,p.link,p.description
FROM ${legacy_schema}.bms_promotion p
JOIN legacy_new_ids ids ON ids.resource='bms_promotion' AND ids.legacy_id=CAST(p.id AS CHAR);

UPDATE bms_tag tag SET topic_count=(SELECT COUNT(*) FROM bms_post_tag link WHERE link.tag_id=tag.id);
UPDATE bms_post post
JOIN legacy_new_ids ids ON ids.resource='bms_post' AND ids.current_id=post.id
SET comments=(
 SELECT COUNT(*) FROM bms_comment c JOIN ums_user author ON author.id=c.user_id
 WHERE c.post_id=post.id AND c.visible=TRUE AND author.status=TRUE);
