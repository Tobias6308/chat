package com.chat.repository;

import com.chat.document.ServiceSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceSessionRepository extends MongoRepository<ServiceSession, String> {

    /**
     * 根据用户ID查找会话
     */
    Optional<ServiceSession> findByUserId(String userId);

    /**
     * 根据客服ID查找会话
     */
    List<ServiceSession> findByServiceId(String serviceId);

    /**
     * 根据客服ID和状态查找会话
     */
    List<ServiceSession> findByServiceIdAndStatus(String serviceId, String status);

    /**
     * 查找等待中的会话
     */
    List<ServiceSession> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * 根据用户ID和状态查找
     */
    Optional<ServiceSession> findByUserIdAndStatus(String userId, String status);

    /**
     * 批量查询用户会话（根据状态）
     */
    List<ServiceSession> findByUserIdInAndStatus(Collection<String> userIds, String status);

    /**
     * 统计客服的会话数
     */
    long countByServiceIdAndStatus(String serviceId, String status);

    /**
     * 分页查询
     */
    Page<ServiceSession> findByServiceId(String serviceId, Pageable pageable);

    /**
     * 根据客服ID和状态分页查询
     */
    Page<ServiceSession> findByServiceIdAndStatus(String serviceId, String status, Pageable pageable);

    /**
     * 根据状态分页查询
     */
    Page<ServiceSession> findByStatus(String status, Pageable pageable);

    /**
     * 根据状态分页查询（按创建时间倒序）
     */
    Page<ServiceSession> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /**
     * 统计总会话数
     */
    long count();

    /**
     * 根据状态统计
     */
    long countByStatus(String status);

    /**
     * 根据用户ID查询会话（按创建时间倒序）
     */
    List<ServiceSession> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * 根据客服ID查询会话（按创建时间倒序）
     */
    List<ServiceSession> findByServiceIdOrderByCreatedAtDesc(String serviceId, Pageable pageable);
}