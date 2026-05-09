package com.chat.repository;

import com.chat.document.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);
    
    @Query("{ $or: [ { 'username': { $regex: ?0, $options: 'i' } }, { 'nickname': { $regex: ?1, $options: 'i' } } ] }")
    Page<User> findByUsernameContainingOrNicknameContaining(String username, String nickname, Pageable pageable);

    @Query("{ $or: [ { 'username': { $regex: ?0, $options: 'i' } }, { 'nickname': { $regex: ?0, $options: 'i' } } ] }")
    List<User> findByUsernameContainingOrNicknameContaining(String keyword);
}