package org.soft.softrear.service;

import org.soft.softrear.pojo.User;
import org.soft.softrear.pojo.dto.UserDto;

import java.util.List;

public interface IUserService {
    /**
     * 插入用户
     *
     * @return
     */
    //增
    User add(UserDto user);
    // 删
    void deleteById(Integer id);
    void deleteByUserName(String userName);
    // 改
    User update(UserDto userDto);

    // 查-根据ID查
    User findById(Integer id);

    // 查-根据用户名查
    User findByUserName(String userName);

    // 查-查所有
    List<User> findAll();
    
    // 登录
    User login(String userName, String passWord);
}
