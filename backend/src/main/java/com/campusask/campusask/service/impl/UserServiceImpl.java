package com.campusask.campusask.service.impl;

import com.campusask.campusask.dto.UserDTO;
import com.campusask.campusask.dto.UserUpdateDTO;
import com.campusask.campusask.entity.User;
import com.campusask.campusask.mapper.UserMapper;
import com.campusask.campusask.service.UserService;
import exception.AccountExistException;
import exception.AccountFormatException;
import exception.PasswordErrorException;
import exception.PasswordNullException;
import exception.exception.RoleErrorException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public void register(UserDTO userDTO) {
        String username = userDTO.getUsername();
        // 优先校验用户名格式（必须是12位数字）
        // 1. 获取身份标识（假设 userDTO 中有 role 字段）
        String role = userDTO.getRole();

        if (username == null) {
            throw new AccountFormatException("注册失败：用户名不能为空");
        }

// 2. 分身份进行不同的格式校验
        if (role != null && role.equals("1")) {
            // ==========================================
            // 管理员校验：T开头 + 12位数字
            // 正则解释：^T 表示必须以 T 开头，\\d{12}$ 表示后面紧跟且只能有 12 位数字
            // ==========================================
            if (!username.matches("^T\\d{12}$")) {
                throw new AccountFormatException("注册失败：管理员账号必须以 T 开头并紧跟 12 位数字（如：T202611020101）");
            }
        } else {
            // ==========================================
            // 学生校验（或默认校验）：刚好 12 位纯数字
            // ==========================================
            if (!username.matches("^\\d{12}$")) {
                throw new AccountFormatException("注册失败：学生用户名必须是 12 位纯数字");
            }
        }

        // 校验密码是否为空
        // trim() 可以防止用户只输入了一堆空格
        if (userDTO.getPassword() == null || userDTO.getPassword().trim().isEmpty()) {
            throw new PasswordNullException("注册失败：密码不能为空");
        }

        //格式合格后，再去数据库检查是否存在重复账号
        User existingUser = userMapper.getByUsername(username);
        if (existingUser != null) {
            throw new AccountExistException("账号已存在");
        }

        // 账号可用，开始创建并封装新用户对象
        User newUser = new User();
        BeanUtils.copyProperties(userDTO, newUser);

        // 设置创建时间
        newUser.setCreateAt(LocalDateTime.now());

        // 第四步：密码加密
        if (newUser.getPassword() != null) {
            String encryptedPwd = DigestUtils.md5DigestAsHex(newUser.getPassword().getBytes());
            newUser.setPassword(encryptedPwd);
        }

        //默认昵称
        if (role != null && role.equals("1")) {
            newUser.setNickname("管理员_" + username);
        } else {
            newUser.setNickname("校园用户_" + username);
        }

        // 第五步：写入数据库
        userMapper.insert(newUser);
    }

    /**
     * 用户登录
     * @param userDTO
     */
    @Override
    public User login(UserDTO userDTO) {
        String username = userDTO.getUsername();
        String password = userDTO.getPassword();

        //根据用户名查询数据库数据
        User user = userMapper.getByUsername(username);

        //处理异常
        if(user == null){
            throw new AccountFormatException("登录失败：用户名不存在");
        }

        // user.getRole() 是数据库里的真实身份，userDTO.getRole() 是前端传过来的
        if (user.getRole() == null || !user.getRole().equals(userDTO.getRole())) {
            throw new RoleErrorException("登录失败：身份不匹配，请重新选择您的登录身份");
        }

        //密码比对
        //跟前端传过来的密码比对MD5铭文加密
        password = DigestUtils.md5DigestAsHex(password.getBytes());
        if(!password.equals(user.getPassword())){
            //密码错误
            throw new PasswordErrorException("登录失败：密码错误");
        }

        return user;
    }

    /**
     * 修改用户个人信息
     * @param username
     * @param userUpdateDTO
     */
    @Override
    public void updateProfile(String username, UserUpdateDTO userUpdateDTO) {
        // 1. 根据当前登录的 username 查出数据库中的老数据
        User user = userMapper.getByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        // 2. 校验并处理新昵称（符合 1-20 位长度要求）
        String newNickname = userUpdateDTO.getNickname();
        if (newNickname == null || newNickname.trim().isEmpty() || newNickname.length() > 20) {
            throw new IllegalArgumentException("昵称长度必须在 1 到 20 个字符之间");
        }
        user.setNickname(newNickname);

        // 3. 密码流处理：如果用户输入了密码框，则触发密码修改
        String oldPwd = userUpdateDTO.getOldPassword();
        String newPwd = userUpdateDTO.getNewPassword();

        if ((oldPwd != null && !oldPwd.isEmpty()) || (newPwd != null && !newPwd.isEmpty())) {
            if (oldPwd == null || oldPwd.isEmpty() || newPwd == null || newPwd.isEmpty()) {
                throw new IllegalArgumentException("修改密码时，原密码和新密码均不能为空");
            }
            if (newPwd.length() < 6) {
                throw new IllegalArgumentException("新密码长度不能少于 6 位");
            }

            // 复用你之前的 MD5 加密对比老密码
            String encryptedOldPwd = DigestUtils.md5DigestAsHex(oldPwd.getBytes());
            if (!encryptedOldPwd.equals(user.getPassword())) {
                throw new IllegalArgumentException("原密码错误，请重新输入");
            }

            // 新密码同样用 MD5 加密后存入数据库
            String encryptedNewPwd = DigestUtils.md5DigestAsHex(newPwd.getBytes());
            user.setPassword(encryptedNewPwd);
        }

        // 4. 更新数据库
        userMapper.updateUser(user);
    }

    /**
     * 根据username获取信息
     * @param username 学号或工号
     * @return
     */
    @Override
    public User getByUsername(String username) {
        User user = userMapper.getByUsername(username);

        // 2. 安全防线：如果查到了用户，把密码字段抹掉，再返回给前端
        if (user != null) {
            user.setPassword(null);
        }

        return user;
    }
}
