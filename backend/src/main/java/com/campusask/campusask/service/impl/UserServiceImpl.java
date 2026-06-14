package com.campusask.campusask.service.impl;

import com.campusask.campusask.dto.PageResult;
import com.campusask.campusask.dto.UserDTO;
import com.campusask.campusask.dto.UserUpdateDTO;
import com.campusask.campusask.entity.ForumPost;
import com.campusask.campusask.entity.QuestionLog;
import com.campusask.campusask.entity.User;
import com.campusask.campusask.mapper.UserMapper;
import com.campusask.campusask.service.UserService;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
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
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {
    // 新增：高并发安全的本地缓存
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> retryCountMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> lockTimeMap = new java.util.concurrent.ConcurrentHashMap<>();

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

        // 新增 1：一进登录，先拦截检查是否处于 15 分钟锁定状态
        if (lockTimeMap.containsKey(username)) {
            long remainMillis = lockTimeMap.get(username) + java.time.Duration.ofMinutes(15).toMillis() - System.currentTimeMillis();
            if (remainMillis > 0) {
                long remainMinutes = (remainMillis / 1000 / 60) + 1; // 向上取整提示
                throw new PasswordErrorException("登录失败：账号因密码错误过多已被锁定，请在 " + remainMinutes + " 分钟后再试！");
            } else {
                lockTimeMap.remove(username); // 满 15 分钟自动解锁
                retryCountMap.remove(username);
            }
        }

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
            // 修改 2：密码对不上了，触发错误计数器逻辑
            int currentRetry = retryCountMap.getOrDefault(username, 0) + 1;
            retryCountMap.put(username, currentRetry);

            if (currentRetry >= 5) {
                lockTimeMap.put(username, System.currentTimeMillis()); // 错满 5 次直接焊死
                throw new PasswordErrorException("登录失败：密码错误已达 5 次！账号已被锁定 15 分钟！");
            }

            int remainCount = 5 - currentRetry;
            throw new PasswordErrorException("登录失败：密码错误，你还可以输入 " + remainCount + " 次");
        }


        // 新增 3：密码正确登录成功
        retryCountMap.remove(username);
        lockTimeMap.remove(username);

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

        // 如果前端传了 motto（哪怕是空字符串 ""，也代表用户想清空或修改名言），就允许覆盖
        if (userUpdateDTO.getMotto() != null) {
            // 可选：限制一下名言长度，防止数据库溢出，比如限制 100 字
            if (userUpdateDTO.getMotto().length() > 100) {
                throw new IllegalArgumentException("个性名言长度不能超过 100 个字符");
            }
            user.setMotto(userUpdateDTO.getMotto());
        }

        // 如果前端传了新的头像 URL，直接覆盖掉老头像
        if (userUpdateDTO.getAvatarUrl() != null && !userUpdateDTO.getAvatarUrl().isEmpty()) {
            user.setAvatarUrl(userUpdateDTO.getAvatarUrl());
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

    /**
     * 查询历史提问记录
     * @param username
     * @param page
     * @param size
     * @return
     */
    @Override
    public PageResult getQuestionHistory(String username, Integer page, Integer size) {

        if (username == null) {
            throw new IllegalArgumentException("查询失败：学号不能为空");
        }

        // 2. 开启 PageHelper 分页拦截器
        PageHelper.startPage(page, size);

        // 3. 调用 Mapper 查 question_log 表（PageHelper 会自动在 SQL 后面追加 limit ?, ? 并查出 total）
        List<QuestionLog> list = userMapper.getQuestionLogByUsername(username);

        // 4. 强转成 Page 对象获取总记录数
        Page<QuestionLog> p = (Page<QuestionLog>) list;

        return new PageResult(p.getTotal(), p.getResult());
    }

    /**
     * 获取茶话会记录

     * @param page
     * @param size
     * @return
     */
    @Override
    public PageResult getForumPostHistory(String username, Integer page, Integer size) {
        if (username == null) {
            throw new IllegalArgumentException("学号不能为空");
        }

        // 启动 PageHelper 物理分页
        PageHelper.startPage(page, size);

        // 拿着学号去查发帖表
        List<ForumPost> list = userMapper.getForumPostsByUsername(username);

        com.github.pagehelper.Page<ForumPost> p = (com.github.pagehelper.Page<ForumPost>) list;
        return new PageResult(p.getTotal(), p.getResult());
    }

    /**
     * 获取点赞记录
     * @param username
     * @return
     */
    @Override
    public List<Map<String, Object>> getUserLikeInteractions(String username) {
        if (username == null) {
            throw new IllegalArgumentException("学号不能为空");
        }
        // 直接穿透调用 Mapper 层的多表联查
        return userMapper.getUserLikeInteractions(username);
    }

    /**
     * 获取评论记录
     * @param username
     * @return
     */
    @Override
    public List<Map<String, Object>> getUserCommentInteractions(String username) {
        if (username == null) {
            throw new IllegalArgumentException("学号不能为空");
        }
        return userMapper.getUserCommentInteractions(username);
    }
}
