import { defineStore } from 'pinia';
import { ref, computed, shallowRef, triggerRef } from 'vue';
import { nanoid } from 'nanoid';
import { friendApi } from '@/utils/api';
import type { Friend, FriendRequest, SearchUserResult } from '@/types/chat';

export const useFriendStore = defineStore('friend', () => {
  // Friends list - use shallowRef for performance
  const friends = shallowRef<Friend[]>([]);

  // Friend requests (received)
  const receivedRequests = ref<FriendRequest[]>([]);

  // Friend requests (sent)
  const sentRequests = ref<FriendRequest[]>([]);

  // Search results
  const searchResults = ref<SearchUserResult[]>([]);

  // Loading states
  const isLoadingFriends = ref(false);
  const isSearching = ref(false);
  const isSendingRequest = ref(false);

  // ============================================
  // Computed
  // ============================================

  /**
   * Get friends count
   */
  const friendsCount = computed(() => friends.value.length);

  /**
   * Get pending requests count
   */
  const pendingRequestsCount = computed(() => receivedRequests.value.filter(r => r.status === 'pending').length);

  /**
   * Check if user is friend
   */
  function isFriend(userId: string): boolean {
    return friends.value.some(f => f.userId === userId);
  }

  /**
   * Get friend by user ID
   */
  function getFriend(userId: string): Friend | undefined {
    return friends.value.find(f => f.userId === userId);
  }

  // ============================================
  // Actions
  // ============================================

  /**
   * Set friends list
   */
  function setFriends(newFriends: Friend[]): void {
    friends.value = newFriends;
    triggerRef(friends);
  }

  /**
   * Add friend
   */
  function addFriend(friend: Friend): void {
    if (!friends.value.some(f => f.id === friend.id)) {
      friends.value = [...friends.value, friend];
    }
  }

  /**
   * Remove friend
   */
  function removeFriend(userId: string): void {
    friends.value = friends.value.filter(f => f.userId !== userId);
  }

  /**
   * Update friend
   */
  function updateFriend(userId: string, updates: Partial<Friend>): void {
    const index = friends.value.findIndex(f => f.userId === userId);
    if (index !== -1) {
      const updated = [...friends.value];
      updated[index] = { ...updated[index], ...updates };
      friends.value = updated;
    }
  }

  /**
   * Set received requests
   */
  function setReceivedRequests(requests: FriendRequest[]): void {
    receivedRequests.value = requests;
  }

  /**
   * Add received request
   */
  function addReceivedRequest(request: FriendRequest): void {
    if (!receivedRequests.value.some(r => r.id === request.id)) {
      receivedRequests.value = [...receivedRequests.value, request];
    }
  }

  /**
   * Update request status
   */
  function updateRequestStatus(requestId: string, status: FriendRequest['status']): void {
    const index = receivedRequests.value.findIndex(r => r.id === requestId);
    if (index !== -1) {
      const updated = [...receivedRequests.value];
      updated[index] = { ...updated[index], status, handledAt: Date.now() };
      receivedRequests.value = updated;
    }

    const sentIndex = sentRequests.value.findIndex(r => r.id === requestId);
    if (sentIndex !== -1) {
      const sentUpdated = [...sentRequests.value];
      sentUpdated[sentIndex] = { ...sentUpdated[sentIndex], status, handledAt: Date.now() };
      sentRequests.value = sentUpdated;
    }
  }

  /**
   * Set sent requests
   */
  function setSentRequests(requests: FriendRequest[]): void {
    sentRequests.value = requests;
  }

  /**
   * Add sent request
   */
  function addSentRequest(request: FriendRequest): void {
    if (!sentRequests.value.some(r => r.id === request.id)) {
      sentRequests.value = [...sentRequests.value, request];
    }
  }

  /**
   * Set search results
   */
  function setSearchResults(results: SearchUserResult[]): void {
    searchResults.value = results;
  }

  /**
   * Clear search results
   */
  function clearSearchResults(): void {
    searchResults.value = [];
  }

  /**
   * Load friends from API
   */
  async function loadFriends(): Promise<void> {
    isLoadingFriends.value = true;
    try {
      const data = await friendApi.getList();
      const friendList: Friend[] = data.friends?.map((f: any) => ({
        id: f.userId,
        userId: f.userId,
        nickname: f.nickname,
        avatar: f.avatar,
        status: 'accepted',
        addedAt: f.addedAt,
        unreadCount: 0
      })) || [];
      setFriends(friendList);
    } catch (error) {
      console.error('Failed to load friends:', error);
    } finally {
      isLoadingFriends.value = false;
    }
  }

  /**
   * Load friend requests from API
   */
  async function loadFriendRequests(): Promise<void> {
    try {
      const data = await friendApi.getRequests();
      
      // Map received requests - use actual status from API
      const received: FriendRequest[] = (data.received || []).map((r: any) => ({
        id: r.requestId,
        fromUserId: r.userId,
        fromNickname: r.nickname,
        fromAvatar: r.avatar,
        toUserId: '',
        status: r.status || 'pending',
        message: '',
        createdAt: r.createdAt
      }));
      setReceivedRequests(received);

      // Map sent requests - use actual status from API
      const sent: FriendRequest[] = (data.sent || []).map((r: any) => ({
        id: r.requestId,
        fromUserId: '',
        fromNickname: '',
        fromAvatar: '',
        toUserId: r.userId,
        toNickname: r.nickname,
        toAvatar: r.avatar,
        status: r.status || 'pending',
        message: '',
        createdAt: r.createdAt
      }));
      setSentRequests(sent);
    } catch (error) {
      console.error('Failed to load friend requests:', error);
    }
  }

  /**
   * Send friend request
   */
  async function sendFriendRequest(toUserId: string, message?: string): Promise<void> {
    isSendingRequest.value = true;
    try {
      await friendApi.sendRequest(toUserId);
      
      const request: FriendRequest = {
        id: `req_${nanoid()}`,
        fromUserId: '',
        fromNickname: '',
        toUserId,
        status: 'pending',
        message,
        createdAt: Date.now()
      };
      addSentRequest(request);
    } catch (error) {
      console.error('Failed to send friend request:', error);
      throw error;
    } finally {
      isSendingRequest.value = false;
    }
  }

  /**
   * Accept friend request
   */
  async function acceptRequest(requestId: string): Promise<void> {
    try {
      await friendApi.acceptRequest(requestId);
      updateRequestStatus(requestId, 'accepted');

      const request = receivedRequests.value.find(r => r.id === requestId);
      if (request) {
        const friend: Friend = {
          id: `friend_${nanoid()}`,
          userId: request.fromUserId,
          nickname: request.fromNickname,
          avatar: request.fromAvatar,
          status: 'accepted',
          addedAt: Date.now(),
          unreadCount: 0
        };
        addFriend(friend);
      }
    } catch (error) {
      console.error('Failed to accept friend request:', error);
      throw error;
    }
  }

  /**
   * Reject friend request
   */
  async function rejectRequest(requestId: string): Promise<void> {
    try {
      await friendApi.rejectRequest(requestId);
      updateRequestStatus(requestId, 'rejected');
    } catch (error) {
      console.error('Failed to reject friend request:', error);
      throw error;
    }
  }

  /**
   * Search users
   */
  async function searchUsers(keyword: string): Promise<SearchUserResult[]> {
    if (!keyword.trim()) {
      clearSearchResults();
      return [];
    }

    isSearching.value = true;
    try {
      const data = await friendApi.search(keyword);
      
      const results: SearchUserResult[] = (data.users || []).map((u: any) => ({
        userId: u.userId,
        nickname: u.nickname,
        avatar: u.avatar,
        isFriend: u.isFriend || false,
        hasPendingRequest: u.hasPendingRequest || false
      }));
      
      setSearchResults(results);
      return results;
    } catch (error) {
      console.error('Failed to search users:', error);
      return [];
    } finally {
      isSearching.value = false;
    }
  }

  /**
   * Remove friend
   */
  async function removeFriendById(userId: string): Promise<void> {
    try {
      await friendApi.removeFriend(userId);
      removeFriend(userId);
    } catch (error) {
      console.error('Failed to remove friend:', error);
      throw error;
    }
  }

  /**
   * Start chat with friend (create or switch to conversation)
   */
  async function startChatWithFriend(userId: string): Promise<string> {
    const friend = getFriend(userId);
    if (!friend) {
      throw new Error('User is not your friend');
    }

    const conversationId = `conv_friend_${userId}`;
    return conversationId;
  }

  // ============================================
  // Initialize
  // ============================================

  // loadFriends();
  // loadFriendRequests();

  return {
    // State
    friends,
    receivedRequests,
    sentRequests,
    searchResults,
    isLoadingFriends,
    isSearching,
    isSendingRequest,

    // Computed
    friendsCount,
    pendingRequestsCount,
    isFriend,
    getFriend,

    // Actions
    setFriends,
    addFriend,
    removeFriend,
    updateFriend,
    setReceivedRequests,
    addReceivedRequest,
    updateRequestStatus,
    setSentRequests,
    addSentRequest,
    setSearchResults,
    clearSearchResults,
    loadFriends,
    loadFriendRequests,
    sendFriendRequest,
    acceptRequest,
    rejectRequest,
    searchUsers,
    removeFriendById,
    startChatWithFriend
  };
});