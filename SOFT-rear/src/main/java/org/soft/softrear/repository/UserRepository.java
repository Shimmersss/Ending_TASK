package org.soft.softrear.repository;

import org.soft.softrear.pojo.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends CrudRepository<User,Integer> {
    Optional<User> findByUserName(String userName);

    void deleteByUserName(String userName);
}
