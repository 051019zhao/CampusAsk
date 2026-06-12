package com.campusask.campusask.mapper;

import com.campusask.campusask.entity.ForumPost;
import com.campusask.campusask.entity.QuestionLog;
import com.campusask.campusask.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {
    /**
     * 根据学号查询是否存在用户
     */
    @Select("select * from user where username = #{username}")
    User getByUsername(String username);

    /**
     * 注册员工
     * @param user
     */
    @Insert("insert into user (username, password, role, created_at, nickname) VALUES " +
            "(#{username}, #{password}, #{role}, #{createAt}, #{nickname})")
    void insert(User user);

    /**
     * 更新用户信息
     * @param user
     */
    void updateUser(User user);

    @Select("SELECT COUNT(*) FROM user")
    int countAll();

    /**
     * 获取提问历史记录
     * @param id
     * @return
     */
    List<QuestionLog> getQuestionLogByUsername(Long id);

    /**
     * 获取茶话会发帖记录
     * @param username
     * @return
     */
    List<ForumPost> getForumPostsByUsername(Long username);

    /**
     * 查询点赞记录
     * @param username
     * @return
     */
    List<Map<String, Object>> getUserLikeInteractions(Long username);

    /**
     * 查询评论记录
     * @param username
     * @return
     */
    List<Map<String, Object>> getUserCommentInteractions(Long username);
}
