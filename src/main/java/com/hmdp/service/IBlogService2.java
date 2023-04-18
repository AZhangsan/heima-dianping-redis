package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;

public interface IBlogService2 extends IService<Blog> {

    Result queryBlogById(Long id);


    /**
     * 喜欢的笔记
     *
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 喜欢的列表
     *
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);


}
