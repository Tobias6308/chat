package com.chat.repository;

import com.chat.document.Friend;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 好友 Repository
 */
@Repository
public interface FriendRepository extends MongoRepository<Friend, String> {
    
    /**
     * 查询用户的好友列表
     * 
     * @param userId 用户 ID
     * @return 好友列表
     */
    List<Friend> findByUserIdAndStatus(String userId, String status);
    
    /**
     * 查询用户的所有好友 (通过 userId)
     * 
     * @param userId 用户 ID
     * @return 好友列表
     */
    @Query("{ 'userId': ?0, 'status': 'accepted' }")
    List<Friend> findAcceptedFriends(String userId);
    
    /**
     * 查询用户的所有好友 (通过 relatedUserId)
     * 
     * @param relatedUserId 关联用户 ID
     * @return 好友列表
     */
    @Query("{ 'relatedUserId': ?0, 'status': 'accepted' }")
    List<Friend> findAcceptedFriendsByRelatedUserId(String relatedUserId);
    
    /**
     * 查询用户发出的好友请求
     * 
     * @param userId 用户 ID
     * @param status 状态
     * @return 好友请求列表
     */
    List<Friend> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status);
    
    /**
     * 查询用户收到的好友请求 (通过 relatedUserId)
     * 
     * @param relatedUserId 关联用户 ID (收到请求的用户)
     * @param status 状态
     * @return 好友请求列表
     */
    @Query("{ 'relatedUserId': ?0, 'status': ?1 }")
    List<Friend> findByRelatedUserIdAndStatusOrderByCreatedAtDesc(String relatedUserId, String status);
    
    /**
     * 查询用户收到的非pending好友请求
     * 
     * @param relatedUserId 关联用户 ID
     * @return 好友请求列表
     */
    @Query("{ 'relatedUserId': ?0, 'status': { $ne: 'pending' } }")
    List<Friend> findByRelatedUserIdAndStatusNotPending(String relatedUserId);
    
    /**
     * 查询用户发出的非pending好友请求
     * 
     * @param userId 用户 ID
     * @return 好友请求列表
     */
    @Query("{ 'userId': ?0, 'status': { $ne: 'pending' } }")
    List<Friend> findByUserIdAndStatusNotPending(String userId);
    
    /**
     * 查询指定好友关系 (兼容旧代码)
     * 
     * @param userId   用户 ID
     * @param friendId 好友 ID
     * @return 好友关系
     */
    Optional<Friend> findByUserIdAndFriendId(String userId, String friendId);
    
    /**
     * 查询指定好友关系 (通过 userId 或 relatedUserId)
     * 
     * @param userId   用户 ID
     * @param relatedUserId 关联用户 ID
     * @return 好友关系
     */
    @Query("{ $or: [ { 'userId': ?0, 'relatedUserId': ?1 }, { 'userId': ?1, 'relatedUserId': ?0 } ] }")
    Optional<Friend> findByUserIdOrRelatedUserId(String userId, String relatedUserId);
    
    /**
     * 统计用户好友数量
     * 
     * @param userId 用户 ID
     * @return 好友数量
     */
    long countByUserIdAndStatus(String userId, String status);
    
    /**
     * 检查是否是好友关系 (兼容旧代码)
     * 
     * @param userId   用户 ID
     * @param friendId 好友 ID
     * @return 是否是好友
     */
    @Query("{ 'userId': ?0, 'friendId': ?1, 'status': 'accepted' }")
    Optional<Friend> isFriend(String userId, String friendId);
    
    /**
     * 检查是否有待处理的好友请求 (兼容旧代码)
     * 
     * @param userId   发送者 ID
     * @param friendId 接收者 ID
     * @return 待处理请求
     */
    @Query("{ 'userId': ?0, 'friendId': ?1, 'status': 'pending' }")
    Optional<Friend> findPendingRequest(String userId, String friendId);
}