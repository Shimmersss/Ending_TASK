package org.soft.softrear.controller;

import jakarta.validation.Valid;
import org.soft.softrear.pojo.ResponseMessage;
import org.soft.softrear.pojo.User;
import org.soft.softrear.pojo.dto.LoginDto;
import org.soft.softrear.pojo.dto.UserDto;
import org.soft.softrear.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController //接口方法返回对象，转化成Json文本

@RequestMapping("/user") //localhost:8080/user/**
public class UserController {
    //Rest方法实现
    @Autowired
    IUserService userService;

    //增
    @PostMapping    //URL: localhost:8080/user/** method : post
    public ResponseMessage<User> add(@Valid @RequestBody UserDto user){
        User userNew = userService.add(user);
        return ResponseMessage.success(userNew);
    }
    // 删 (Delete)
    @DeleteMapping("/{id}")
    public ResponseMessage<Void> deleteById(@PathVariable Integer id) {
        userService.deleteById(id);
        // 删除成功后，返回一个成功消息，data 部分可以为空
        return new ResponseMessage<>(200, "id： "+id+" 删除成功", null);
    }
    @DeleteMapping("/name/{userName}")
    public ResponseMessage<Void> deleteByUserName(@PathVariable String userName) {
        userService.deleteByUserName(userName);
        // 删除成功后，返回一个成功消息，data 部分可以为空
        return new ResponseMessage<>(200, "删除成功", null);
    }

    // 改 (Update)
    @PutMapping("/find")
    public ResponseMessage<User> update(@Valid @RequestBody UserDto userDto) {
        User updatedUser = userService.update(userDto);
        return ResponseMessage.success(updatedUser);
    }

    // 查-根据ID查 (Retrieve by ID)
    @GetMapping("/{id}")
    public ResponseMessage<User> findById(@PathVariable Integer id) {
        User user = userService.findById(id);
        return ResponseMessage.success(user);
    }

    // 查-根据用户名查 (Retrieve by UserName)
    @GetMapping("/name/{userName}")
    public ResponseMessage<User> findByUserName(@PathVariable String userName) {
        User user = userService.findByUserName(userName);
        return ResponseMessage.success(user);
    }

    // 查-查所有 (Retrieve all)
    @GetMapping
    public ResponseMessage<List<User>> findAll() {
        List<User> users = userService.findAll();
        return ResponseMessage.success(users);
    }

    // 登录
    @PostMapping("/login")
    public ResponseMessage<User> login(@Valid @RequestBody LoginDto loginDto) {
        User user = userService.login(loginDto.getUserName(), loginDto.getPassWord());
        if (user != null) {
            return ResponseMessage.success(user);
        } else {
            return new ResponseMessage<>(401, "用户名或密码错误", null);
        }
    }

}
