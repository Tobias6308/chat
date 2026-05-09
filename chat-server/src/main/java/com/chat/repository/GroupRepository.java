package com.chat.repository;

import com.chat.document.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 群组 Repository
 */
@Repository
public interface GroupRepository extends MongoRepository<Group, String> {
    
    /**
     * 查询用户加入的群组列表
     * 
     * @param userId 用户 ID
     * @return 群组列表
     */
    @Query("{ 'members.userId': ?0 }")
    List<Group> findByUserId(String userId);
    
    /**
     * 查询用户创建的群组列表
     * 
     * @param ownerId 群主 ID
     * @return 群组列表
     */
    List<Group> findByOwnerId(String ownerId);
    
    /**
     * 根据群名模糊搜索
     * 
     * @param name 群名关键字
     * @param limit 数量限制
     * @return 群组列表
     */
    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    List<Group> findByNameLike(String name, int limit);
    
    /**
     * 根据群名模糊搜索（分页）
     */
    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    Page<Group> findByNameContaining(String name, Pageable pageable);
    
    /**
     * 查询用户是否为群成员
     * 
     * @param groupId 群 ID
     * @param userId 用户 ID
     * @return 是否是成员
     */
    @Query("{ 'id': ?0, 'members.userId': ?1 }")
    Optional<Group> findByGroupIdAndMemberUserId(String groupId, String userId);
    
    /**
     * 获取群成员数量
     * 
     * @param groupId 群 ID
     * @return 成员数量
     */
    @Query(value = "{ '_id': ?0 }", count = true)
    long countById(String groupId);
}