package com.liushao.dao;

import com.liushao.model.entity.BmsTip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import com.liushao.model.vo.PostVO;

public interface BmsTopicDao extends JpaRepository<PostVO,Integer>, JpaSpecificationExecutor<PostVO> {

    @Query(value = "select t.id,t.title,t.user_id,t.comments,t.view,t.collects,t.top,t.essence,t.create_time ,t.modify_time,u.username,u.alias, u.avatar from bms_post t LEFT JOIN ums_user u ON t.user_id = u.id", nativeQuery = true)
    BmsTip findRandomTip();
}
