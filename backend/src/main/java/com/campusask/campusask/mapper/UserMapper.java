package com.campusask.campusask.mapper;

import com.campusask.campusask.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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
}
