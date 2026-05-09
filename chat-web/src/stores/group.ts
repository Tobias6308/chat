import { defineStore } from 'pinia';
import { ref, computed, shallowRef, triggerRef } from 'vue';
import { nanoid } from 'nanoid';
import { groupApi } from '@/utils/api';
import type { Group, GroupMember, GroupMemberRole, GroupJoinRequest, GroupInvite } from '@/types/chat';

export const useGroupStore = defineStore('group', () => {
  // Groups list
  const groups = shallowRef<Group[]>([]);

  // Join requests (for group admins)
  const joinRequests = ref<GroupJoinRequest[]>([]);

  // Invitations (received)
  const invitations = ref<GroupInvite[]>([]);

  // Loading states
  const isLoadingGroups = ref(false);
  const isCreatingGroup = ref(false);
  const isLoadingMembers = ref(false);

  // Current group (for management view)
  const currentGroup = ref<Group | null>(null);

  // ============================================
  // Computed
  // ============================================

  /**
   * Get groups count
   */
  const groupsCount = computed(() => groups.value.length);

  /**
   * Get pending join requests count
   */
  const pendingJoinRequestsCount = computed(() =>
    joinRequests.value.filter(r => r.status === 'pending').length
  );

  /**
   * Get my groups (where I'm owner or admin)
   */
  const myAdminGroups = computed(() =>
    groups.value.filter(g =>
      g.members.some(m => m.userId === 'current_user' && (m.role === 'owner' || m.role === 'admin'))
    )
  );

  /**
   * Get group by ID
   */
  function getGroupById(groupId: string): Group | undefined {
    return groups.value.find(g => g.id === groupId);
  }

  /**
   * Check if user is admin of group
   */
  function isGroupAdmin(groupId: string, userId: string): boolean {
    const group = getGroupById(groupId);
    if (!group) return false;
    const member = group.members.find(m => m.userId === userId);
    return member?.role === 'owner' || member?.role === 'admin';
  }

  /**
   * Check if user is owner of group
   */
  function isGroupOwner(groupId: string, userId: string): boolean {
    const group = getGroupById(groupId);
    if (!group) return false;
    return group.ownerId === userId;
  }

  /**
   * Get member in group
   */
  function getMember(groupId: string, userId: string): GroupMember | undefined {
    const group = getGroupById(groupId);
    return group?.members.find(m => m.userId === userId);
  }

  // ============================================
  // Actions
  // ============================================

  /**
   * Set groups
   */
  function setGroups(newGroups: Group[]): void {
    groups.value = newGroups;
    triggerRef(groups);
  }

  /**
   * Add group
   */
  function addGroup(group: Group): void {
    if (!groups.value.some(g => g.id === group.id)) {
      groups.value = [...groups.value, group];
    }
  }

  /**
   * Update group
   */
  function updateGroup(groupId: string, updates: Partial<Group>): void {
    const index = groups.value.findIndex(g => g.id === groupId);
    if (index !== -1) {
      const updated = [...groups.value];
      updated[index] = { ...updated[index], ...updates };
      groups.value = updated;
      if (currentGroup.value?.id === groupId) {
        currentGroup.value = updated[index];
      }
    }
  }

  /**
   * Remove group
   */
  function removeGroup(groupId: string): void {
    groups.value = groups.value.filter(g => g.id !== groupId);
    if (currentGroup.value?.id === groupId) {
      currentGroup.value = null;
    }
  }

  /**
   * Load groups from API
   */
  async function loadGroups(): Promise<void> {
    isLoadingGroups.value = true;
    try {
      const response = await groupApi.getList() as any;
      const data = response.groups || [];
      
      const groupList: Group[] = data.map((g: any) => ({
        id: g.id,
        name: g.name,
        description: g.description,
        avatar: g.avatar,
        ownerId: g.ownerId || '',
        myRole: g.myRole || 'member',
        members: [],
        memberCount: g.memberCount || 0,
        createdAt: g.createdAt,
        pinned: g.pinned || false,
        muted: g.muted || false,
        unreadCount: g.unreadCount || 0,
        isMuted: g.muted || false,
        conversationId: g.conversationId || g.id
      }));
      
      setGroups(groupList);
    } catch (error) {
      console.error('Failed to load groups:', error);
    } finally {
      isLoadingGroups.value = false;
    }
  }

  /**
   * Create new group
   */
  async function createGroup(name: string, description?: string, memberIds: string[] = []): Promise<Group> {
    isCreatingGroup.value = true;
    try {
      const data = await groupApi.create({
        name,
        description,
        memberIds
      });

      const group: Group = {
        id: data.id || `group_${nanoid()}`,
        name: data.name || name,
        description: data.description || description,
        ownerId: 'current_user',
        members: [],
        memberCount: memberIds.length + 1,
        createdAt: Date.now(),
        pinned: false,
        muted: false,
        unreadCount: 0,
        isMuted: false,
        conversationId: data.id || `group_${nanoid()}`
      };

      addGroup(group);
      return group;
    } catch (error) {
      console.error('Failed to create group:', error);
      throw error;
    } finally {
      isCreatingGroup.value = false;
    }
  }

  /**
   * Set current group (for management view)
   */
  async function setCurrentGroup(groupId: string | null): Promise<void> {
    if (groupId) {
      const group = getGroupById(groupId);
      if (group) {
        // Load members from API
        try {
          const response = await groupApi.getMembers(groupId) as any;
          const members = response.members || [];
          const memberList: GroupMember[] = members.map((m: any) => ({
            userId: m.userId,
            nickname: m.nickname,
            avatar: m.avatar,
            role: m.role || 'member',
            joinedAt: m.joinedAt
          }));
          updateGroup(groupId, { members: memberList });
          currentGroup.value = { ...getGroupById(groupId)!, members: memberList };
        } catch (error) {
          console.error('Failed to load group members:', error);
          currentGroup.value = group;
        }
      } else {
        currentGroup.value = null;
      }
    } else {
      currentGroup.value = null;
    }
  }

  /**
   * Add members to group
   */
  async function addMembersToGroup(groupId: string, memberIds: string[]): Promise<void> {
    try {
      await groupApi.addMembers(groupId, memberIds);
      // Reload members
      await setCurrentGroup(groupId);
      // Refresh conversation data
      const { useConversationStore } = await import('@/stores/conversation');
      const conversationStore = useConversationStore();
      await conversationStore.loadFromApi();
    } catch (error) {
      console.error('Failed to add members:', error);
      throw error;
    }
  }

  /**
   * Remove member from group
   */
  async function removeMemberFromGroup(groupId: string, userId: string): Promise<void> {
    try {
      await groupApi.removeMember(groupId, userId);
      // Update local state
      const group = getGroupById(groupId);
      if (group) {
        const updatedMembers = group.members.filter(m => m.userId !== userId);
        updateGroup(groupId, {
          members: updatedMembers,
          memberCount: updatedMembers.length
        });
      }
      // Refresh conversation data
      const { useConversationStore } = await import('@/stores/conversation');
      const conversationStore = useConversationStore();
      await conversationStore.loadFromApi();
    } catch (error) {
      console.error('Failed to remove member:', error);
      throw error;
    }
  }

  /**
   * Update member role
   */
  async function updateMemberRole(groupId: string, userId: string, role: GroupMemberRole): Promise<void> {
    try {
      await groupApi.setMemberRole(groupId, userId, role);
      
      const group = getGroupById(groupId);
      if (!group) return;

      const memberIndex = group.members.findIndex(m => m.userId === userId);
      if (memberIndex === -1) return;

      const updatedMembers = [...group.members];
      updatedMembers[memberIndex] = { ...updatedMembers[memberIndex], role };
      updateGroup(groupId, { members: updatedMembers });
      
      // Refresh conversation data
      const { useConversationStore } = await import('@/stores/conversation');
      const conversationStore = useConversationStore();
      await conversationStore.loadFromApi();
    } catch (error) {
      console.error('Failed to update member role:', error);
      throw error;
    }
  }

  /**
   * Remove member from group
   */
  async function removeMember(groupId: string, userId: string): Promise<void> {
    try {
      await groupApi.removeMember(groupId, userId);
      
      const group = getGroupById(groupId);
      if (!group) return;

      // Can't remove owner
      if (group.ownerId === userId) return;

      const updatedMembers = group.members.filter(m => m.userId !== userId);
      updateGroup(groupId, {
        members: updatedMembers,
        memberCount: updatedMembers.length
      });
      
      // Refresh conversation data
      const { useConversationStore } = await import('@/stores/conversation');
      const conversationStore = useConversationStore();
      await conversationStore.loadFromApi();
    } catch (error) {
      console.error('Failed to remove member:', error);
      throw error;
    }
  }

  /**
   * Mute/unmute member
   */
  async function toggleMemberMute(groupId: string, userId: string): Promise<void> {
    const group = getGroupById(groupId);
    if (!group) return;

    const member = group.members.find(m => m.userId === userId);
    if (!member) return;

    const newMuted = !member.muted;

    try {
      await groupApi.setMemberMute(groupId, userId, newMuted);
      // Update local state
      const memberIndex = group.members.findIndex(m => m.userId === userId);
      if (memberIndex === -1) return;

      const updatedMembers = [...group.members];
      updatedMembers[memberIndex] = {
        ...updatedMembers[memberIndex],
        muted: newMuted
      };
      updateGroup(groupId, { members: updatedMembers });
      
      // Refresh conversation data
      const { useConversationStore } = await import('@/stores/conversation');
      const conversationStore = useConversationStore();
      await conversationStore.loadFromApi();
    } catch (error) {
      console.error('Failed to toggle mute:', error);
      throw error;
    }
  }

  /**
   * Set member role (admin/member)
   */
  async function setMemberRole(groupId: string, userId: string, role: string): Promise<void> {
    try {
      await groupApi.setMemberRole(groupId, userId, role);
      // Update local state
      const group = getGroupById(groupId);
      if (!group) return;

      const memberIndex = group.members.findIndex(m => m.userId === userId);
      if (memberIndex === -1) return;

      const updatedMembers = [...group.members];
      updatedMembers[memberIndex] = {
        ...updatedMembers[memberIndex],
        role: role as 'owner' | 'admin' | 'member'
      };
      updateGroup(groupId, { members: updatedMembers });
    } catch (error) {
      console.error('Failed to set member role:', error);
      throw error;
    }
  }

  /**
   * Add join request
   */
  function addJoinRequest(request: GroupJoinRequest): void {
    if (!joinRequests.value.some(r => r.id === request.id)) {
      joinRequests.value = [...joinRequests.value, request];
    }
  }

  /**
   * Set join requests
   */
  function setJoinRequests(requests: GroupJoinRequest[]): void {
    joinRequests.value = requests;
  }

  /**
   * Handle join request
   */
  function handleJoinRequest(requestId: string, accept: boolean): void {
    const request = joinRequests.value.find(r => r.id === requestId);
    if (!request) return;

    const index = joinRequests.value.findIndex(r => r.id === requestId);
    const updated = [...joinRequests.value];
    updated[index] = { ...request, status: accept ? 'accepted' : 'rejected', handledAt: Date.now() };
    joinRequests.value = updated;

    if (accept) {
      const group = getGroupById(request.groupId);
      if (group) {
        const newMember: GroupMember = {
          userId: request.userId,
          nickname: request.nickname,
          avatar: request.avatar,
          role: 'member',
          joinedAt: Date.now()
        };
        const updatedMembers = [...group.members, newMember];
        updateGroup(request.groupId, {
          members: updatedMembers,
          memberCount: updatedMembers.length
        });
      }
    }
  }

  /**
   * Add invitation
   */
  function addInvitation(invite: GroupInvite): void {
    if (!invitations.value.some(i => i.id === invite.id)) {
      invitations.value = [...invitations.value, invite];
    }
  }

  /**
   * Set invitations
   */
  function setInvitations(invites: GroupInvite[]): void {
    invitations.value = invites;
  }

  /**
   * Handle invitation
   */
  function handleInvitation(inviteId: string, accept: boolean): void {
    const invite = invitations.value.find(i => i.id === inviteId);
    if (!invite) return;

    const index = invitations.value.findIndex(i => i.id === inviteId);
    const updated = [...invitations.value];
    updated[index] = { ...invite, status: accept ? 'accepted' : 'rejected', handledAt: Date.now() };
    invitations.value = updated;

    if (accept) {
      const group = getGroupById(invite.groupId);
      if (group) {
        const newMember: GroupMember = {
          userId: 'current_user',
          nickname: '我',
          role: 'member',
          joinedAt: Date.now()
        };
        const updatedMembers = [...group.members, newMember];
        updateGroup(invite.groupId, {
          members: updatedMembers,
          memberCount: updatedMembers.length
        });
      }
    }
  }

  /**
   * Toggle pin group
   */
  async function togglePin(groupId: string): Promise<void> {
    const group = getGroupById(groupId);
    if (!group) return;

    try {
      const result = await groupApi.togglePin(groupId);
      if (result.success) {
        updateGroup(groupId, { pinned: result.pinned });
      }
    } catch (error) {
      console.error('Toggle pin failed:', error);
    }
  }

  /**
   * Toggle mute group
   */
  async function toggleMute(groupId: string): Promise<void> {
    const group = getGroupById(groupId);
    if (!group) return;

    try {
      const result = await groupApi.toggleMute(groupId);
      if (result.success) {
        updateGroup(groupId, { muted: result.muted });
      }
    } catch (error) {
      console.error('Toggle mute failed:', error);
    }
  }

  /**
   * Leave group
   */
  async function leaveGroup(groupId: string): Promise<void> {
    const group = getGroupById(groupId);
    if (!group) return;

    if (group.ownerId === 'current_user') {
      return;
    }

    try {
      await groupApi.delete(groupId);
      removeGroup(groupId);
    } catch (error) {
      console.error('Failed to leave group:', error);
      throw error;
    }
  }

  /**
   * Dissolve group (owner only)
   */
  async function dissolveGroup(groupId: string): Promise<void> {
    try {
      await groupApi.delete(groupId);
      removeGroup(groupId);
    } catch (error) {
      console.error('Failed to dissolve group:', error);
      throw error;
    }
  }

  /**
   * Update group info
   */
  async function updateGroupInfo(groupId: string, name?: string, description?: string, avatar?: string): Promise<void> {
    try {
      await groupApi.update(groupId, { name, description });
      updateGroup(groupId, {
        name: name || getGroupById(groupId)?.name,
        description: description !== undefined ? description : getGroupById(groupId)?.description,
        avatar: avatar !== undefined ? avatar : getGroupById(groupId)?.avatar,
        updatedAt: Date.now()
      });
    } catch (error) {
      console.error('Failed to update group:', error);
      throw error;
    }
  }

  // ============================================
  // Initialize
  // ============================================

  // loadGroups();

  return {
    // State
    groups,
    joinRequests,
    invitations,
    isLoadingGroups,
    isCreatingGroup,
    isLoadingMembers,
    currentGroup,

    // Computed
    groupsCount,
    pendingJoinRequestsCount,
    myAdminGroups,
    getGroupById,
    isGroupAdmin,
    isGroupOwner,
    getMember,

    // Actions
    setGroups,
    addGroup,
    updateGroup,
    removeGroup,
    loadGroups,
    createGroup,
    setCurrentGroup,
    addMembersToGroup,
    removeMemberFromGroup,
    updateMemberRole,
    removeMember,
    toggleMemberMute,
    setMemberRole,
    addJoinRequest,
    setJoinRequests,
    handleJoinRequest,
    addInvitation,
    setInvitations,
    handleInvitation,
    togglePin,
    toggleMute,
    leaveGroup,
    dissolveGroup,
    updateGroupInfo
  };
});