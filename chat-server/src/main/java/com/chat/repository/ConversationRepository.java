package com.chat.repository;

import com.chat.document.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 会话 Repository
 */
@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {
    
    /**
     * 查询用户的会话列表
     * 
     * @param userId     用户 ID
     * @param limit     数量限制
     * @return 会话列表
     */
    @Query("{ 'participants': ?0 }")
    List<Conversation> findByParticipantsContaining(String userId, int limit);
    
    /**
     * 查询用户的会话列表 (按更新时间排序, 去重)
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    @Query(value = "{ 'participants': ?0 }", sort = "{ 'updatedAt': -1 }")
    List<Conversation> findUserConversationsDistinct(String userId);
    
    /**
     * 根据参与者查询会话 (私聊)
     * 
     * @param userId1 用户 ID 1
     * @param userId2 用户 ID 2
     * @return 会话实体
     */
    @Query("{ 'type': 'private', 'participants': { $all: [?0, ?1], $size: 2 } }")
    Optional<Conversation> findPrivateConversation(String userId1, String userId2);
    
    /**
     * 查询用户参与的私聊会话
     * 
     * @param userId 用户 ID
     * @param type 会话类型
     * @return 会话列表
     */
    @Query("{ 'participants': ?0, 'type': ?1 }")
    List<Conversation> findByParticipantsContainingAndType(String userId, String type);
    
    /**
     * 批量查询会话
     * 
     * @param ids 会话 ID 列表
     * @return 会话列表
     */
    List<Conversation> findByIdIn(List<String> ids);
    
    /**
     * 统计用户会话数量
     * 
     * @param userId 用户 ID
     * @return 会话数量
     */
    long countByParticipantsContaining(String userId);
    
    /**
     * 根据名称模糊搜索（分页）
     */
    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    Page<Conversation> findByNameContaining(String name, Pageable pageable);

    /**
     * 根据类型查询会话（分页）
     */
    Page<Conversation> findByType(String type, Pageable pageable);
    
    /**
     * 根据关联 ID 查询会话
     * 
     * @param relateId 关联 ID (群 ID 或好友记录 ID)
     * @return 会话实体
     */
    Optional<Conversation> findByRelateId(String relateId);
    
    /**
     * 根据关联 ID 和类型查询会话
     * 
     * @param relateId 关联 ID
     * @param type 会话类型 (private/group)
     * @return 会话实体
     */
    Optional<Conversation> findByRelateIdAndType(String relateId, String type);
}