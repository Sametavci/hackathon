import { ApiError, request, type RequestOptions } from './client';
import { mockRequest } from './mocks';
import { t } from '../i18n/en';
import type { CardsResponse, ConnectionItem, ConnectChoice, GroupResponse, InvitePreview, InviteResponse, MatchRunResponse, MeResponse, MetaResponse, RegistrationRequest, RegistrationResponse, SeedResponse, StatsResponse } from './types';
const call = <T>(path: string, options?: RequestOptions): Promise<T> => import.meta.env.VITE_USE_MOCKS === 'true' ? mockRequest<T>(path, options) : request<T>(path, options);
const participant = { auth: 'participant' } as const;
const segment = encodeURIComponent;
const connectionsPath = (groupId: string) => `/groups/${segment(groupId)}/connections`;
export const getMeta = () => call<MetaResponse>('/meta');
export const registerParticipant = (body: RegistrationRequest) => call<RegistrationResponse>('/participants', { method: 'POST', body });
export const getMe = () => call<MeResponse>('/me', participant);
export const deleteMe = () => call<void>('/me', { ...participant, method: 'DELETE' });
export const createInvite = () => call<InviteResponse>('/me/invite', { ...participant, method: 'POST' });
export const getInvite = (code: string) => call<InvitePreview>(`/invites/${segment(code)}`);
export const unlinkFriend = () => call<MeResponse>('/me/friend/unlink', { ...participant, method: 'POST' });
export const getGroup = (id: string) => call<GroupResponse>(`/groups/${segment(id)}`, participant);
export const getCards = (id: string) => call<CardsResponse>(`/groups/${segment(id)}/cards`, participant);
export const getConnections = (groupId: string) => call<ConnectionItem[]>(connectionsPath(groupId), participant);
export const setConnection = (groupId: string, id: string, choice: ConnectChoice) => call<ConnectionItem>(`${connectionsPath(groupId)}/${segment(id)}`, { ...participant, method: 'PUT', body: { choice } });
export const shareContact = (groupId: string, id: string, contact: string) => call<ConnectionItem>(`${connectionsPath(groupId)}/${segment(id)}/contact`, { ...participant, method: 'PUT', body: { contact } });
export const withdrawContact = (groupId: string, id: string) => call<ConnectionItem>(`${connectionsPath(groupId)}/${segment(id)}/contact`, { ...participant, method: 'DELETE' });
export const seedDemo = (adminKey: string) => call<SeedResponse>('/admin/seed', { auth: 'admin', adminKey, method: 'POST' });
export const runMatch = (adminKey: string) => call<MatchRunResponse>('/admin/match', { auth: 'admin', adminKey, method: 'POST' });
export const getStats = (adminKey: string) => call<StatsResponse>('/admin/stats', { auth: 'admin', adminKey });
// A3's success status/body is missing from the frozen contract. No network mutation
// until Dev 1 specifies it. Keep the public entry point for the second integration pass.
export const resetDemo = (): Promise<never> => Promise.reject(new ApiError(0, 'CONTRACT_PENDING', t('admin.resetPending')));
