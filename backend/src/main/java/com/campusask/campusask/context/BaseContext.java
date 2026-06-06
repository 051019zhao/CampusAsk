package com.campusask.campusask.context;

public class BaseContext {

    // 保留原有的用户 ID 存储（Long 类型）
    private static final ThreadLocal<Long> idThreadLocal = new ThreadLocal<>();

    // 新增：用户学号/用户名存储（String 类型）
    private static final ThreadLocal<String> usernameThreadLocal = new ThreadLocal<>();

    // ==================== ID 相关方法 ====================
    public static void setCurrentId(Long id) {
        idThreadLocal.set(id);
    }

    public static Long getCurrentId() {
        return idThreadLocal.get();
    }

    public static void removeCurrentId() {
        idThreadLocal.remove();
    }

    // ==================== Username 相关方法（对应拦截器） ====================
    public static void setCurrentUsername(String username) {
        usernameThreadLocal.set(username);
    }

    public static String getCurrentUsername() {
        return usernameThreadLocal.get();
    }

    public static void removeCurrentUsername() {
        usernameThreadLocal.remove();
    }

    /**
     * 清理当前线程的所有数据，防止内存泄漏
     * 建议在请求结束（如拦截器的 afterCompletion 中）调用
     */
    public static void clear() {
        idThreadLocal.remove();
        usernameThreadLocal.remove();
    }
}