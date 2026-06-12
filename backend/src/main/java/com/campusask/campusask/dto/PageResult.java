package com.campusask.campusask.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

/**
 * 统一分页结果包装对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 总记录数（用于前端算总页数：historyTotal）
     */
    private Long total;

    /**
     * 当前页的数据列表（用于前端 v-for 循环：historyList）
     */
    private List<?> records;
}
