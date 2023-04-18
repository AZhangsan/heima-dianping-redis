package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService2;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BlogServiceImpl2 extends ServiceImpl<BlogMapper, Blog> implements IBlogService2 {

    @Resource
    private IUserService iUserService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        buildBlogByUser(blog);
        setIsLike(blog, user.getId());

        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long userId = user.getId();
        String key = "blog:likes:" + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        // 点过赞，取消赞，将点赞数减一
        if (score != null) {
            update().setSql("liked = liked - 1").eq("id", id).update();
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            return Result.ok("取消赞成功");
        } else {
            // 未点赞，添加赞，将点赞数加一
            update().setSql("liked = liked + 1").eq("id", id).update();
            stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            return Result.ok("点赞成功");
        }
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = "blog:likes:" + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 5);
        if (CollectionUtil.isEmpty(top5)) {
            return Result.ok(Collections.emptyList());
        }
        // 2.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<User> users = iUserService.query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        List<UserDTO> userDTOList = users.stream().map(u -> BeanUtil.copyProperties(u, UserDTO.class)).collect(Collectors.toList());

        return Result.ok(userDTOList);
    }

    private void buildBlogByUser(Blog blog) {
        User user = iUserService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    private void setIsLike(Blog blog, Long userId) {
        String key = "blog:likes:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(Boolean.TRUE.equals(score != null));
    }

}
