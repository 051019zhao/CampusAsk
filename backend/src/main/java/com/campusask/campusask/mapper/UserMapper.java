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

    /** V4: 重置密码 */
    @org.apache.ibatis.annotations.Update("UPDATE user SET password = #{password} WHERE username = #{username}")
    int updatePassword(@org.apache.ibatis.annotations.Param("username") String username, @org.apache.ibatis.annotations.Param("password") String password);

    /** V4: 查询所有用户 */
    @Select("SELECT * FROM user ORDER BY created_at DESC")
    java.util.List<User> findAll();

    /** V4: 删除用户 */
    @org.apache.ibatis.annotations.Delete("DELETE FROM user WHERE username = #{username}")
    int deleteByUsername(@org.apache.ibatis.annotations.Param("username") String username);

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
    List<QuestionLog> getQuestionLogByUsername(String username);

    /**
     * 获取茶话会发帖记录
     * @param username
     * @return
     */
    List<ForumPost> getForumPostsByUsername(String username);

    /**
     * 查询点赞记录
     * @param username
     * @return
     */
    List<Map<String, Object>> getUserLikeInteractions(String username);

    /**
     * 查询评论记录
     * @param username
     * @return
     */
    List<Map<String, Object>> getUserCommentInteractions(String username);
}
