package com.chat.repository;

import com.chat.document.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 消息 Repository
 * 
 * 提供消息的 CRUD 和分页查询
 */
@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    /**
     * 获取会话的最新消息
     *
     * @param conversationId 会话 ID
     * @return 最新消息
     */
    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(String conversationId);

    /**
     * 获取会话的最新消息列表
     *
     * @param conversationId 会话 ID
     * @param limit 数量限制
     * @return 消息列表
     */
    List<Message> findTop10ByConversationIdOrderByCreatedAtDesc(String conversationId, org.springframework.data.domain.Pageable pageable);
    
    /**
     * 查询会话消息 (分页)
     * 
     * @param conversationId 会话 ID
     * @param pageable 分页参数
     * @return 消息分页
     */
    Page<Message> findByConversationIdOrderByCreatedAtDesc(
        String conversationId, 
        Pageable pageable
    );
    
    /**
     * 查询会话消息 (游标分页)
     * 
     * @param conversationId 会话 ID
     * @param createdAt     游标时间
     * @param limit         数量限制
     * @return 消息列表
     */
    @Query("{ 'conversationId': ?0, 'createdAt': { $lt: ?1 } }")
    List<Message> findByConversationIdWithCursor(
        String conversationId, 
        Long createdAt, 
        int limit
    );
    
    /**
     * 查询会话消息 (按时间正序)
     * 
     * @param conversationId 会话 ID
     * @param limit 数量限制
     * @return 消息列表
     */
    List<Message> findTopByConversationIdOrderByCreatedAtAsc(
        String conversationId, 
        int limit
    );
    
    /**
     * 统计会话消息数量
     * 
     * @param conversationId 会话 ID
     * @return 消息数量
     */
    long countByConversationId(String conversationId);
    
    /**
     * 根据 ID 查询消息
     * 
     * @param id 消息 ID
     * @return 消息实体
     */
    Optional<Message> findById(String id);
    
    /**
     * 批量查询消息
     * 
     * @param ids 消息 ID 列表
     * @return 消息列表
     */
    List<Message> findByIdIn(List<String> ids);
    
    /**
     * 删除会话的所有消息
     * 
     * @param conversationId 会话 ID
     * @return 删除数量
     */
    long deleteByConversationId(String conversationId);
    
    /**
     * 根据会话ID查询消息（分页）
     */
    Page<Message> findByConversationId(String conversationId, Pageable pageable);
    
    /**
     * 根据内容模糊搜索（分页）
     */
    @Query("{ 'content': { $regex: ?0, $options: 'i' } }")
    Page<Message> findByContentContaining(String content, Pageable pageable);

    /**
     * 根据发送者查询消息（分页）
     */
    Page<Message> findBySenderId(String senderId, Pageable pageable);

    /**
     * 根据会话ID和创建时间查询消息（用于获取离线消息）
     */
    List<Message> findByConversationIdAndCreatedAtGreaterThan(String conversationId, Long createdAt);

    /**
     * 根据会话ID查询消息（分页，按时间正序）
     */
    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId, org.springframework.data.domain.Pageable pageable);

    /**
     * 批量查询多个会话的最新消息（按时间倒序）
     */
    List<Message> findTopByConversationIdInOrderByCreatedAtDesc(List<String> conversationIds, int limit);
}