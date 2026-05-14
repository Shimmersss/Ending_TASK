package org.soft.softrear.service;

import jakarta.transaction.Transactional;
import org.soft.softrear.pojo.User;
import org.soft.softrear.pojo.dto.UserDto;
import org.soft.softrear.repository.UserRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service //Spring的bean
public class UserService implements IUserService{
    @Autowired
    UserRepository userRepository;
    @Override
    public User add(UserDto userDto) {
        // 检查用户名是否已存在
        /*
        Optional<User> existingUser = userRepository.findByUserName(userDto.getUserName());
        if (existingUser.isPresent()) {
            throw new RuntimeException("用户名已存在");
        }
        */
        User user = new User();
        BeanUtils.copyProperties(userDto, user);
        return userRepository.save(user);
    }

    @Override
    public void deleteById(Integer id) {
        // JpaRepository 的 deleteById 方法在找不到记录时会抛出 EmptyResultDataAccessException
        // 我们这里直接调用，如果前端传了一个不存在的ID，全局异常处理器会捕获并处理它
        userRepository.deleteById(id);
    }
    @Override
    @Transactional
    public void deleteByUserName(String userName) {
        // 1. 先根据用户名查找用户，确认用户是否存在
        Optional<User> userOptional = userRepository.findByUserName(userName);

        // 2. 如果用户不存在，抛出异常，由全局异常处理器返回友好提示
        User user = userOptional.orElseThrow(() -> new RuntimeException("用户不存在，用户名: " + userName));

        // 3. 如果用户存在，调用 Repository 的删除方法
        userRepository.deleteByUserName(userName);
    }


    @Override
    public User update(UserDto userDto) {
        // 更新操作需要先根据ID查询出已存在的用户
        // 假设 UserDto 中包含了 userId
        User existingUser = userRepository.findById(userDto.getUserId())
                .orElseThrow(() -> new RuntimeException("用户不存在，ID: " + userDto.getUserId()));

        // 更新字段 (通常不更新ID和用户名)
        existingUser.setPassWord(userDto.getPassWord());
        existingUser.setEmail(userDto.getEmail());

        return userRepository.save(existingUser);
    }

    @Override
    public User findById(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在，ID: " + id));
    }

    @Override
    public User findByUserName(String userName) {
        return userRepository.findByUserName(userName)
                .orElseThrow(() -> new RuntimeException("用户不存在，用户名: " + userName));
    }

    @Override
    public List<User> findAll() {
        return (List<User>) userRepository.findAll();
    }

    @Override
    public User login(String userName, String passWord) {
        Optional<User> userOptional = userRepository.findByUserName(userName);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            // 这里简单比较密码，实际项目中应该使用加密处理
            if (passWord.equals(user.getPassWord())) {
                return user;
            }
        }
        return null;
    }
}
